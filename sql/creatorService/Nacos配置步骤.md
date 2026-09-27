# 创作者后台 v1 — Nacos 配置变更步骤

> Nacos 控制台：http://192.168.100.128:8848 （nacos/nacos）
> 本文件只列配置变更，**不改 gateway 代码**。

## 1. gateway-routes.json（DEFAULT_GROUP）— 改：数组追加两条路由

在现有 RouteDefinition JSON 数组中追加：

```json
{"id":"creator-service","uri":"lb://creator-service","predicates":[{"name":"Path","args":{"pattern":"/creator/**"}}],"filters":[]}
{"id":"web-auth-service","uri":"lb://user-service","predicates":[{"name":"Path","args":{"pattern":"/web/**"}}],"filters":[]}
```

说明：
- `/creator/**` → creator-service（后台业务）。
- `/web/**` → user-service（**双账号域拆分后新增**：Web 后台认证 `/web/auth/**`）。
- `/users/**` 已有到 user-service 的路由（wx 域），无需新增。
- 完整快照见 `sql/gateway/gateway-routes.json`。

## 2. gateway-dev.yaml — 改：追加 CORS（网页端跨域唯一入口）

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origin-patterns:
              - "http://localhost:*"
              - "http://127.0.0.1:*"
              # 生产环境替换为正式域名
            allowed-methods: ["GET","POST","PUT","DELETE","OPTIONS"]
            allowed-headers: ["*"]
            allow-credentials: true
            max-age: 3600
```

## 3. 鉴权白名单（hm.auth.excludePaths 所在配置）— 仅确认

必须已包含：`/users/auth/login/password`、`/web/auth/{captcha,register,login}`、`/auth/refresh-token`。
**不得**加入 `/creator/**`、`/web/auth/password/change` 与 `/users/auth/phone{,/bind}`
（后台全部业务接口与改绑/改密接口都必须登录）。

> ⚠️ 该文件（网关本地 `application.yml`）**不在 git 中**，改动需人工同步到部署环境；
> 完整白名单与 `realmPaths` 见 `docs/双账号域拆分-上线与回归.md` 第 3 节。

## 4. trybe-creator-dev.yaml — 新建（creator-service 私有配置）

骨架如下，AK/SK 类敏感配置只放 Nacos，不进代码库：

```yaml
# 阿里云人脸人体识别（模特图质检 FaceDetectService 使用）
aliyun:
  facebody:
    access-key-id: <填实际AK>
    access-key-secret: <填实际SK>
    endpoint: facebody.cn-shanghai.aliyuncs.com
    region-id: cn-shanghai
```

说明：OSS（aliyun.oss.*）与 Redis、JDBC 均复用 shared-oss.yaml / shared-redis.yaml / shared-jdbc.yaml，无需重复配置。

## 5. shared-oss.yaml — **改**：新增第二 bucket（头像 / 主页封面图独立存储）

```yaml
aliyun:
  oss:
    endpoint: oss-cn-qingdao.aliyuncs.com
    access-key-id: <既有AK>
    access-key-secret: <既有SK>
    bucket-name: trybe                        # 默认桶：物品图 / 模特图 / 试穿素材（既有，不改，私有）
    bucket-name2: trybe-avatar                # 【新增】第二桶：创作者头像 / 主页封面图
    bucket-name2-public-read: true            # 【新增】第二桶为公共读 → 下发永久 URL，不再预签名
    # bucket-name2-domain:                    # 【可选】第二桶绑定的 CDN/自定义域名，不配则走 OSS 默认域名
    domain:                                   # 默认桶绑定的自定义域名（既有，不改）
    expire-seconds: 3600
```

说明：

- 两个桶**同 endpoint、同 AK/SK**，只是存储空间不同 —— 因此没有再建一套配置类，
  只在读写/签名时显式传目标 bucket 名（见 `OssService` 的 bucket 重载）。
- `bucket-name2` 是**可选配置**：不配（或配空）时自动回落 `bucket-name`，行为等价单桶；
  本地/测试环境可以先不配，不影响启动。
- `bucket-name2-public-read` 控制下发的 URL 形态：`true` → 永久公开 URL；
  `false`/不配 → 预签名 URL。**默认 false 是安全的回落方向**：
  桶权限与配置不一致时最多是多算一次签名，不会把私有对象用公开 URL 暴露出去。
- URL 形态由 `OssService.accessUrl(bucket, key)` 统一判定，业务侧不感知权限细节：
  桶权限改了只改配置，代码不动。
- ⚠️ **该文件是共享配置**，别名/上传都读它。若某服务不应看到第二桶，则不要给它共享这份配置
  （当前 5 个服务都通过 `bootstrap.yaml` 引入 `shared-oss.yaml`，此处按「共享」处理）。
- **红线**：同一个 objectKey 的「上传 / 归档 / 删除 / 取流 / 签名」必须传同一个 bucket。
  creator-service 侧已统一收敛为 `UploadScene.isProfileImageScene()` 判定，
  禁止在业务代码里各写一遍 bucket 三元表达式。

### 配套的 OSS 控制台操作（不在代码库内）

1. 确认 `trybe-avatar` 已创建，且**地域与默认桶一致**（oss-cn-qingdao），否则跨域访问会有额外延迟。
2. 把 `trybe-avatar` 的读写权限设为**公共读**（私有写）：
   控制台 → `trybe-avatar` → 权限管理 → 读写权限 → 公共读。
   这样头像/封面可直接用永久 URL 访问，页面还能被浏览器与 CDN 缓存。
3. 浏览器直传 PUT 仍需在 **`trybe-avatar`** 上配置跨域规则（公共读只解决「读」，不解决「跨域写」）。

   **方式 A：ossutil（推荐，可复现、可进版本库）**

   ```bash
   ossutil cors --method put oss://trybe-avatar sql/creatorService/oss-cors-trybe-avatar.xml
   ossutil cors --method get oss://trybe-avatar      # 回读确认
   ```

   规则文件见同目录 `oss-cors-trybe-avatar.xml`（已含注释与上线替换说明）。

   **方式 B：OSS 控制台**

   直达链接（登录后直接到跨域设置页）：

   ```
   https://oss.console.aliyun.com/bucket/oss-cn-qingdao/trybe-avatar/permission/cors
   ```

   菜单路径：Bucket 列表 → `trybe-avatar` → 左侧「权限管理 / 权限控制」→「跨域设置」→「创建规则」。

   | 字段 | 值 | 说明 |
   |---|---|---|
   | 来源 / AllowedOrigin | `http://localhost:*`、`http://127.0.0.1:*`（**分两行添加**） | 本地 dev server；端口会漂移故用通配。上线换成正式域名 |
   | 允许 Methods | `PUT`、`GET`、`HEAD`、`POST` | `PUT` 是直传必需的，缺了就等于没配 |
   | 允许 Headers | `*` | 必须放行 `Content-Type`，否则预签名 PUT 被拒 |
   | 暴露 Headers | `ETag` | 供前端校验上传完整性 |
   | 缓存时间 | `3600` | 减少 OPTIONS 预检次数 |

   保存后**约 1 分钟内生效**。

   **验证命令**（返回 200 且带 `Access-Control-Allow-Origin` 即成功）：

   ```bash
   curl -i -X OPTIONS "https://trybe-avatar.oss-cn-qingdao.aliyuncs.com/creator/1/tmp/t.jpg" \
     -H "Origin: http://127.0.0.1:5173" \
     -H "Access-Control-Request-Method: PUT" \
     -H "Access-Control-Request-Headers: content-type"
   ```

   仍返回 `403 CORSResponse: CORS is not enabled for this bucket` → 规则没保存成功，检查是否配到了 `trybe` 桶上。

> 小程序端 `wx.request`/`wx.uploadFile` 不受同源策略约束，所以此前只在网页端上传时才会暴露 CORS 缺失问题。
>
> 展示侧注意：`<img src="永久URL">` 这种标签加载**不受同源策略限制**，不需要 CORS；
> 但如果前端改用 `fetch`/`XHR` 去读图片内容，则仍需 CORS 放行 `GET`。
>
> ⚠️ 公共读桶 + 宽松 CORS 的风险：来源若长期保留 `*`，任意站点都能往你的桶里 PUT。
> 开发期图方便可以，上线务必换成正式域名。

## 6. shared-jdbc.yaml / shared-redis.yaml — 复用不改

creator-service 与其他服务同库 `trybe`、同 Redis。

## 7. 存量数据：已入库的头像/封面 OSS 对象需迁移

把对象从默认桶 `trybe` 搬到第二桶 `trybe-avatar`，**objectKey 保持不变**
（`creator/{userId}/profile/avatar.{ext}` / `cover.{ext}`），因此 **DB 无需改数据**，只搬对象。

- 图形化：OSS 控制台 → `trybe` 桶 → 勾选 `creator/` 下 `*/profile/*` 对象 → 复制到 `trybe-avatar`（保持路径）→ 确认后删除源对象。
- 命令行走 `ossutil`（推荐，可整前缀复制）：

```bash
# 1) 跨桶同路径复制（仅 profile 目录，即头像与封面）
ossutil cp -r oss://trybe/creator/ oss://trybe-avatar/creator/ \
  --include "*/profile/*" --update

# 2) 核对数量无误后，删源（可选）
ossutil rm -r oss://trybe/creator/ --include "*/profile/*" -f
```

- 迁移期间注意顺序：**先加 `bucket-name2` 配置 → 再搬对象 → 再发版**，
  否则搬完对象但代码还在用默认桶，会读到空对象。
- 以后新上传的头像/封面会直接落 `trybe-avatar`，`trybe` 桶内不会再新增 `profile` 对象。
