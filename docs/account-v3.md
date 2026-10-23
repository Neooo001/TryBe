# 统一账号体系 v3（2026-10-23）

> 一句话：**一个人 = 一个账号，手机号是唯一身份锚点**；小程序与创作者后台共用 `app_user`，  
> `realm` 从「账号域」降级为「端/会话」。

---

## 1. 与 v2（双账号域拆分）的差别

| 维度      | v2                                  | v3                    |
| ------- | ----------------------------------- | --------------------- |
| 账号表     | `wx_user`（小程序）+ `web_user`（后台），零关联  | `app_user` 单表         |
| 同一手机号   | 两表各存一份，是两个人                         | 只能属于一个账号              |
| 绑号撞号    | **抢号**：把手机号从 A 转移到 B，A 置 `RELEASED` | **归并**：B 并入 A，数据随手机号走 |
| 创作者身份   | 有 `web_user` 行 = 创作者                | 不再区分，人人可创作            |
| `realm` | 账号域（代表不同的人）                         | 端/会话（同一人两端并存，防跨端越权）   |
| 浏览圈子    | 免登录                                 | 必须登录且已绑号              |

## 2. 数据模型

```
app_user（人）
├─ id / nickname / avatar / status / bio / gender / unionid
├─ phone_state      0从未绑定 1已绑定 2曾绑定被释放
├─ register_source  WX / WEB —— 账号首次诞生端，不再改变
├─ last_login_time  沉睡 180 天判定依据
└─ merged_into_user_id  status=2 时指向被并入的账号（仅审计）

user_identity（凭据，一人 N 条）
└─ uk_type_app_identifier(identity_type, app_id, identifier)
   PHONE          → app_id=''，identifier=手机号
   WECHAT_OPENID  → app_id=小程序appid，identifier=openid

user_password（1:1）   密码，与手机号解耦（手机号易主密码随人走）
user_phone_history     曾绑定的号 + 释放原因（PHONE_TRANSFERRED / DORMANT_TAKEOVER）
phone_transfer_log     归属变更审计（BIND_PHONE / PHONE_REGISTER / ACCOUNT_MERGE / DORMANT_TAKEOVER）
creator_profile.creator_id / creator_item.creator_id  →  app_user.id
```

**手机号只能通过短信验证码写入**：没有任何接口接受「直接设置手机号」，  
`resolveOrCreateByPhone` / `bindPhone` 两个入口都先 `verifySmsCode`。

## 3. 登录矩阵

| 端   | 方式     | 接口                                | 说明                                     |
| --- | ------ | --------------------------------- | -------------------------------------- |
| 小程序 | 微信一键登录 | `POST /users/login`               | openid 未注册 → 建号 + `needBindPhone=true` |
| 小程序 | 手机号密码  | `POST /users/auth/login/password` | 保留，非必需                                 |
| Web | 短信验证码  | `POST /web/auth/login/sms`        | 未注册提示「请先注册」                            |
| Web | 手机号+密码 | `POST /web/auth/login`            | 无密码账号提示「请先设置密码」                        |
| Web | 注册     | `POST /web/auth/register`         | 手机号+短信码，取或建                            |

发码：

- `POST /users/auth/sms/code`、`POST /web/auth/sms/code`：免鉴权，需图形码 + 限流
- `POST /users/auth/sms/code/authed`、`POST /web/auth/sms/code/set-password`：需登录，免图形码，号码由服务端取

## 4. 资料统一（2026-10-23 追加）

**问题**：昵称 / 头像 / 简介原来存了两份 —— `app_user`（小程序读）和 `creator_profile`（后台 + 圈子作品卡片读），
在 Web 改完资料，小程序看到的还是旧值。

**结论**：`app_user` 是唯一资料源，**改一次，小程序 / 后台 / 圈子作品署名全变**。

| 字段 | 唯一存放位置 | 修改入口 |
|---|---|---|
| 昵称 `nickname` | `app_user.nickname` | 小程序 `PUT /users/profile`；后台 `PUT /web/users/profile` |
| 头像 `avatar` | `app_user.avatar`（objectKey） | 同上 |
| 简介 `bio` | `app_user.bio` | 同上 |
| 主页封面 `cover_key` | `creator_profile`（创作者专属） | `PUT /creator/profile` |
| 创作方向 `field_codes` | `creator_profile` | `PUT /creator/profile` |
| 主页展示状态 | `creator_profile`（只读） | 管理端 |

- `creator_profile.nickname / avatar_key / bio` 三列 **已冻结**（数据保留、代码不再读写），
  观察期后再 DROP。迁移脚本 `sql/account/20261023_merge_profile.sql`（creator 侧有值则覆盖 app_user）。
- `PUT /creator/profile` 入参只剩 `coverKey` / `fieldCodes`；
  昵称头像简介改走 user-service 的 `GET|PUT /web/users/profile`（realm=web，`/web/**` 路由到 user-service）。
- 圈子作品卡片（`PublicWorkReadServiceImpl`）改读 `app_user`（`AccountBrief` 批量查询，一次 `IN`，无 N+1）。
- 头像桶本来就是同一个 `trybe-avatar`，只是路径约定不同（小程序 `avatars/{uid}/uuid.ext`、
  后台 `creator/{uid}/profile/avatar.ext`），读时都走 `accessUrl(secondBucket, key, version)`，不需要搬文件。

## 5. 三条核心规则

**① 新用户门禁（小程序）**  
`splash` 拿到 `needBindPhone` → `wx.reLaunch` 到 `bind-phone?mode=force`（页面栈只有它 +  
`hideHomeButton`，无法返回）→ 绑号成功后 `reLaunch` 到 `set-password`（可跳过）→ 首页。  
圈子 `onLoad/onShow` 双重校验：无登录态回开屏页，`phoneState≠BOUND` 送强制绑号页。

**② 绑号归并（不是抢号）**

```
bindPhone(当前账号 B, 手机号 P)
  P 无人持有      → 直接绑给 B
  P 属于 B 自己   → 幂等
  P 属于 A ≠ B    → B 并入 A：openid 等凭据迁到 A、B 标记 status=2+MERGED、
                    A.phone_state=BOUND、下发 A 的新令牌（前端必须整套替换）
```

带安全阀 `assertNoBusinessData`：B 若已有 `creator_item` / `wardrobe_item`，拒绝合并并报错  
（宁可报错，不可静默丢数据）。这也说明「绑号前不许进主界面」是归并的前提。

**③ 注册取或建（Web）**

```
register(手机号 P)
  P 无账号           → 新建 app_user(register_source=WEB, nickname=随机) + PHONE 凭据
  P 属活跃账号       → 直接沿用该账号（小程序先注册 → 沿用小程序数据，昵称不变）
  P 属沉睡账号(≥180天) → 释放手机号（写 history + 冻结旧账号）→ 新建，走「二次放号」语义
```

前端据 `newUser` / `hasPassword` 决定跳首页还是先去设置密码。

## 6. 接口变更清单

新增：

- `POST /web/auth/sms/code`（免鉴权，图形码）
- `POST /web/auth/login/sms`（免鉴权）
- `POST /web/auth/sms/code/set-password`（需登录）
- `POST /web/auth/password/set`（需登录）

语义变化：

- `POST /web/auth/register`：入参从 `{phone,password,captchaId,captchaCode}` → `{phone, code, nickname?}`
- `POST /users/auth/phone/bind`：返回体新增 `merged` / `hasPassword` / `nickname`，合并时额外返回一套令牌
- `WebLoginVO` 新增 `hasPassword` / `newUser` / `registerSource` / `phone`
- `UserLoginVO` 新增 `needBindPhone` / `hasPassword`

网关（`TryBe-gateway/application.yml`）：

- 白名单移除 `/community/**`（浏览圈子需登录），`realmPaths.wx` 增加 `/community/**`
- 白名单增加 `/web/auth/sms/code`、`/web/auth/login/sms`
- `TryBe-common/MvcConfig` 同步这两条

## 7. 迁移与回滚

脚本：`sql/account/20261023_unified_account.sql`（已执行）  
备份：`sql/account/backup/before_unified_account_20261023.sql`

执行内容：建 `app_user` → 迁 `wx_user`（保 id）→ 按手机号并入 `web_user`（密码搬 `user_password`）  
→ `creator_*.creator_id` 重映射 → 旧表改名 `*_bak_20261023`（**未 DROP**）。

回滚：

```sql
RENAME TABLE app_user TO app_user_v3_bak,
             wx_user_bak_20261023 TO wx_user,
             web_user_bak_20261023 TO web_user,
             web_user_archive_bak_20261023 TO web_user_archive;
-- creator_profile/creator_item.creator_id 若已改，需按备份文件恢复这两表数据
```

## 8. 验证步骤

1. 重启 `user-service`、`creator-service`、`TryBe-gateway`  
   （⚠️ 表已改名，**旧进程不重启会直接报 `wx_user doesn't exist`**）
2. `curl -X POST localhost:8080/web/auth/captcha` → 应返回 captchaId + 图片
3. Web 注册：图形码 → 发短信 → 注册 → 应返回 `hasPassword=false` 并跳设置密码页
4. 用小程序的同号账号走「微信登录 → 绑同号」→ 应返回 `merged=true` + 新令牌，作品仍在
5. 圈子不带 token 直接请求 `/community/works` → 应 401（不再是免登录）
6. 在后台个人中心改昵称/头像/简介 → 小程序「我的」+ 圈子作品卡片应同步变化（同一份 `app_user`）

## 9. 已知限制

- 服务端只校验「有合法令牌」，**不校验 `phone_state`**；绑号门禁在客户端（splash/圈子）。  
  要彻底堵死需网关或 content-service 读 `phone_state`，本期未做。
- 小程序若被分享链接直达非开屏页，可能绕过 splash 门禁（首页请求失败会静默续命建号，  
  但不会自动跳绑号页）。
- 沉睡账号被接管后，原账号若有作品，作品仍挂在其 `app_user.id` 上，但其已无法登录 ——  
  需要管理端「转移作品」能力，本期未做。
- 微信 `getPhoneNumber` 未接入（个人主体无权限），手机号只能手输 + 短信验证码。
