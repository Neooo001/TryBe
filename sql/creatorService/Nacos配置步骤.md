# 创作者后台 v1 — Nacos 配置变更步骤

> Nacos 控制台：http://192.168.100.128:8848 （nacos/nacos）
> 本文件只列配置变更，**不改 gateway 代码**。

## 1. gateway-routes.json（DEFAULT_GROUP）— **整份覆盖，不要手工合并条目**

> ⚠️ **两条硬规矩（都踩过坑，务必照做）**
>
> **① `pattern` 必须写成裸字符串，绝不能写成 JSON 数组。**
>
> 网关的 `DynamicRouteLoader` 用 Hutool 反序列化这份配置，而 Spring Cloud Gateway 的
> `PredicateDefinition.args` 声明类型是 `Map<String, String>` —— 值是**字符串**，不是列表。
> Hutool 遇到数组会把它原样转成一个字符串，`pattern` 的值变成字面量
> `["/users/**","/web/**","/auth/**"]`（**含方括号和引号**）；随后 Spring 的
> String→List 转换（`StringToCollectionConverter`，按逗号切分）再把这个字符串切成
> `["/users/**` / `"/web/**"` / `"/auth/**"]` 三段带方括号引号的碎片。
>
> 后果：**这条路由永远匹配不上任何请求**。实测结果如下（AntPathMatcher 真实匹配）：
>
> | pattern 写法 | 绑定出的规则 | 匹配 `/users/login` |
> |---|---|---|
> | `["/users/**","/web/**","/auth/**"]` | `["/users/**` / `"/web/**"` / `"/auth/**"]` | **✗ 未命中** |
> | `/users/**`（裸字符串） | `/users/**` | ✓ 命中 |
>
> 症状：后台登录 `/web/auth/login`、刷新令牌 `/auth/refresh-token`、wx 域 `/users/**`
> 全部 **404**（路由没注册上，网关直接 404，`AuthGlobalFilter` 甚至不会执行）。
>
> **② 一个 `id` 对应一条路径。** 需要多个路径就多写几条路由条目，
> 不要把 `/users/**`、`/web/**`、`/auth/**` 合并进同一条。

**操作：用 `sql/gateway/gateway-routes.json` 的内容整份覆盖 Nacos 里的这个配置项。**

> 📌 **唯一权威来源就是 `sql/gateway/gateway-routes.json`**，不要再去找别的副本。
> 原先本目录下曾有一份 `sql/creatorService/gateway-routes.json`，内容**缺 `/web/**` 这一条**
> （双账号域拆分前的旧版），正是诱使「手工把三条路径合并成一条」的源头，**已删除**。
> 如需找回：`git checkout 73f3c4d -- sql/creatorService/gateway-routes.json`（但请勿再用它覆盖 Nacos）。

```json
[
  { "id": "user-service",        "uri": "lb://user-service",        "predicates": [{ "name": "Path", "args": { "pattern": "/users/**" } }],    "filters": [] },
  { "id": "web-auth-service",    "uri": "lb://user-service",        "predicates": [{ "name": "Path", "args": { "pattern": "/web/**" } }],      "filters": [] },
  { "id": "user-auth-service",   "uri": "lb://user-service",        "predicates": [{ "name": "Path", "args": { "pattern": "/auth/**" } }],     "filters": [] },
  { "id": "creator-service",     "uri": "lb://creator-service",     "predicates": [{ "name": "Path", "args": { "pattern": "/creator/**" } }],  "filters": [] },
  { "id": "material-service",    "uri": "lb://material-service",    "predicates": [{ "name": "Path", "args": { "pattern": "/materials/**" } }], "filters": [] },
  { "id": "body-profile-service","uri": "lb://body-profile-service","predicates": [{ "name": "Path", "args": { "pattern": "/profiles/**" } }],  "filters": [] },
  { "id": "wardrobe-service",    "uri": "lb://wardrobe-service",    "predicates": [{ "name": "Path", "args": { "pattern": "/wardrobe/**" } }],  "filters": [] }
]
```

路径归属：

| 路径 | 目标服务 | 账号域 | 说明 |
|---|---|---|---|
| `/users/**` | user-service | wx | 小程序域认证与用户接口 |
| `/web/**` | user-service | web | **双账号域拆分后新增**：Web 后台认证 `/web/auth/**` |
| `/auth/**` | user-service | any | 跨域共享：刷新令牌 / 登出 |
| `/creator/**` | creator-service | web | 创作者后台业务 |
| `/materials/**` | material-service | wx | 素材库 |
| `/profiles/**` | body-profile-service | wx | 身材档案 |
| `/wardrobe/**` | wardrobe-service | wx | 衣橱 |

**验证**（发布后无需重启网关，`DynamicRouteLoader` 监听 Nacos 变更并热更新路由）：

```bash
# ① 探路由是否注册：返回 404 = 路由没生效；返回业务 JSON（含 code 字段）= 路由已通
curl -s -o /dev/null -w "web=%{http_code}\n"  "http://localhost:8080/web/auth/captcha"
curl -s -o /dev/null -w "creator=%{http_code}\n" "http://localhost:8080/creator/items"

# ② 探鉴权是否兜住：未带 token 访问受保护接口，应是 {"code":4010x,...} 而不是 404
curl -s "http://localhost:8080/creator/items"
```

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

## 5.1 默认桶 `trybe` 的跨域规则 — **新增（作品图片直传必需）**

> **背景（踩过的坑）**：第 5 节只给 `trybe-avatar` 配了 CORS。创作者后台的「发布作品」会把
> 作品图按 `MODEL_IMAGE` 场景归档到**默认桶 `trybe`**（`creator/{userId}/models/xxx.jpg`），
> 而默认桶没有 CORS 规则 → 浏览器 PUT 直传报 `net::ERR_FAILED`。
>
> **它不是签名问题**：预签名 URL 只解决「授权写入」。浏览器对 OSS 的 **PUT 请求必然先发 OPTIONS 预检**
> —— PUT 在任何情况下都不属于 CORS「简单请求」，与是否携带 `Content-Type` 无关。
> 所以这个坑**无法用代码绕过**（除非改成服务端中转上传），只能在桶上放行。

### 控制台操作

直达链接：

```
https://oss.console.aliyun.com/bucket/oss-cn-qingdao/trybe/permission/cors
```

菜单路径：Bucket 列表 → **`trybe`** → 左侧「权限管理 / 权限控制」→「跨域设置」→「创建规则」。
字段与第 5 节表格**完全一致**（来源 `http://localhost:*` 与 `http://127.0.0.1:*` 分两行添加；
方法勾 `PUT/GET/HEAD/POST`；允许 Headers 填 `*`；暴露 Headers 填 `ETag`；缓存 `3600`）。

### ossutil（推荐，可复现）

```bash
# 规则文件见 sql/creatorService/oss-cors-trybe-default.xml
ossutil cors --method put oss://trybe sql/creatorService/oss-cors-trybe-default.xml
ossutil cors --method get oss://trybe       # 回读确认
```

### 验证命令（返回 200 且带 Access-Control-Allow-Origin 即成功）

```bash
curl -i -X OPTIONS "http://trybe.oss-cn-qingdao.aliyuncs.com/creator/1/tmp/t.png" \
  -H "Origin: http://127.0.0.1:5173" \
  -H "Access-Control-Request-Method: PUT" \
  -H "Access-Control-Request-Headers: content-type"
```

返回 `403 CORSResponse: CORS is not enabled for this bucket` → 规则没生效，确认配的是 `trybe` 桶
（很多人会误配到 `trybe-avatar`，因为那份规则文件先存在）。

> 两个桶都要各配一条规则，规则**不跨桶生效**。改完约 1 分钟生效。

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
