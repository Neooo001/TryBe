# 创作者后台 v1 — Nacos 配置变更步骤

> Nacos 控制台：http://192.168.100.128:8848 （nacos/nacos）
> 本文件只列配置变更，**不改 gateway 代码**。

## 1. gateway-routes.json（DEFAULT_GROUP）— 改：数组追加一条路由

在现有 RouteDefinition JSON 数组中追加：

```json
{"id":"creator-service","uri":"lb://creator-service","predicates":[{"name":"Path","args":{"pattern":"/creator/**"}}],"filters":[]}
```

说明：`/users/**` 已有到 user-service 的路由，入驻接口（/users/creator/**、/users/admin/creator/**）复用该路由，无需新增。

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

必须已包含：`/users/phone/login`、`/auth/refresh-token`（登录/刷新链路）。
**不得**加入 `/creator/**` 与 `/users/admin/**`（创作者后台全部接口要求登录）。

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
