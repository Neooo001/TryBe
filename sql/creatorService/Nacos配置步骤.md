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

## 5. shared-jdbc.yaml / shared-oss.yaml / shared-redis.yaml — 复用不改

creator-service 与其他服务同库 `trybe`、同 OSS bucket、同 Redis。
