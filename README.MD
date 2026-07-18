# TryBe

基于 Spring Cloud 微服务架构的电商后端系统，采用网关统一入口 + 多个微服务独立部署的架构模式。

## 项目模块

```
TryBe
├── TryBe-common      # 公共模块：工具类、统一响应、异常处理、自动配置
├── TryBe-api         # RPC 接口模块：Feign Client 声明 + DTO + 降级处理
├── TryBe-gateway     # 网关服务：认证鉴权、动态路由、请求转发
└── user-service      # 用户服务：登录认证、用户管理、JWT 签发
```

### 模块依赖关系

```
TryBe-gateway  ──▶  TryBe-common
user-service   ──▶  TryBe-common
TryBe-api      ──▶  TryBe-common
user-service   ──▶  TryBe-api        (间接依赖，通过 Feign 调用)
```

## 技术选型

| 类别 | 技术 | 版本 |
|---|---|---|
| 基础框架 | Spring Boot | 2.7.12 |
| 微服务治理 | Spring Cloud + Spring Cloud Alibaba | 2021.0.3 / 2021.0.4.0 |
| 注册中心 & 配置中心 | Nacos | — |
| API 网关 | Spring Cloud Gateway | — |
| 远程调用 | OpenFeign + LoadBalancer | — |
| ORM | MyBatis-Plus | 3.4.2 |
| 数据库 | MySQL | 8.0.23 |
| 安全认证 | JWT (RSA + JKS) + Spring Security (BCrypt) | — |
| 消息队列 | RabbitMQ (AMQP) | — |
| 接口文档 | Knife4j (Swagger) | 4.1.0 |
| 工具库 | Hutool | 5.8.11 |
| 构建工具 | Maven | — |
| Java 版本 | JDK 11 (common) / JDK 17 (各服务) | — |

## 快速开始

### 环境准备

- JDK 17+
- Maven 3.8+
- MySQL 8.0
- Nacos Server
- RabbitMQ（可选，common 模块集成了 AMQP 依赖）

### 1. 配置数据库

在 `user-service` 的配置文件中设置数据库连接信息：

- `application-local.yaml` — 本地开发环境
- `application-dev.yaml` — 开发/测试环境（通过 Nacos 配置中心管理共享 JDBC 配置）

### 2. 配置 Nacos

编辑各服务的 `bootstrap.yaml`，设置 Nacos 地址与认证信息：

```yaml
spring:
  cloud:
    nacos:
      server-addr: <nacos-ip>:8848
      username: nacos
      password: nacos
```

### 3. 编译打包

```bash
mvn clean package -DskipTests
```

### 4. 启动服务

建议启动顺序：Nacos → Gateway → 各微服务

```bash
# 启动网关（端口 8080）
cd TryBe-gateway
mvn spring-boot:run

# 启动用户服务（端口 8084）
cd user-service
mvn spring-boot:run
```

## 核心功能

### 网关认证

网关层通过 `AuthGlobalFilter` 对所有请求进行 JWT 校验。配置白名单路径（如 `/users/login`）可跳过认证，其余请求必须携带有效 Token。

配置示例 (`application.yml`)：

```yaml
hm:
  jwt:
    location: classpath:TryBe.jks    # JWT 密钥库
    alias: TryBe
    password: 123456
    tokenTTL: 30m
  auth:
    excludePaths:
      - /users/login
```

### 用户上下文传递

网关认证通过后，将用户 ID 写入请求头 `user-info`。下游微服务通过 `MvcConfig` 注册的拦截器自动将用户 ID 存入 `UserContext`（ThreadLocal），OpenFeign 调用时再通过 `RequestInterceptor` 自动透传，实现全链路用户信息传递。

### 统一响应格式

所有接口返回 `R<T>` 结构：

```json
{
  "code": 200,
  "msg": "OK",
  "data": { }
}
```

### 统一异常处理

`CommonExceptionAdvice` 全局捕获 `CommonException` 及其子类（`BadRequestException`、`ForbiddenException`、`BizIllegalException`、`DbException`、`UnauthorizedException`），转换为统一响应格式。

### 动态路由

`DynamicRouteLoader` 监听 Nacos 中的 `gateway-routes.json` 配置变更，实现运行时动态更新网关路由，无需重启。

## 项目结构说明

### TryBe-common

| 包 | 说明 |
|---|---|
| `config` | MyBatis 分页插件、MVC 拦截器注册、JSON 序列化配置、RabbitMQ 消息转换器 |
| `domain` | 通用实体：`R<T>`（统一响应）、`PageQuery`、`PageDTO`（分页） |
| `exception` | 继承体系：`CommonException` → `BadRequestException`(400)、`ForbiddenException`(403)、`UnauthorizedException`(401)、`BizIllegalException`、`DbException` |
| `utils` | `UserContext`（ThreadLocal 用户上下文）、`BeanUtils`、`CollUtils`、`Convert`、`CookieBuilder`、`WebUtils` |
| `interceptors` | `UserInfoInterceptor` — 从请求头解析用户 ID 并存入 ThreadLocal |
| `advice` | `CommonExceptionAdvice` — 全局异常处理 |

### TryBe-api

- `client` — Feign 接口声明：`UserClient`、`ItemClient`、`CartClient`、`PayClient`、`TradeClient`
- `client/fallback` — 降级工厂：`ItemClientFallbackFactory`、`PayClientFallback`
- `dto` — 跨服务传输对象：`ItemDTO`、`OrderDetailDTO`、`PayOrderDTO`
- `config` — `DefaultFeignConfig`：Feign 日志级别 + 用户信息透传拦截器

### TryBe-gateway

- `filters/AuthGlobalFilter` — 全局 JWT 认证过滤器
- `filters/MyGlobalFilter` — 自定义过滤器（示例/扩展点）
- `routers/DynamicRouteLoader` — 监听 Nacos 动态更新路由表
- `config` — `AuthProperties`（白名单配置）、`JwtProperties`（密钥库配置）、`SecurityConfig`（密钥对 Bean）

### user-service

- `controller/UserController` — 登录接口 (`POST /users/login`)
- `service/IUserService` + `impl/UserServiceImpl` — 登录业务逻辑（校验用户状态、密码、生成 Token）
- `mapper/UserMapper` — MyBatis-Plus BaseMapper
- `domain/po/User` — 用户实体（对应 `user` 表）
- `domain/dto/LoginFormDTO` — 登录请求体
- `domain/vo/UserLoginVO` — 登录响应（userId + username + balance + token）
- `config` — `JwtProperties`、`SecurityConfig`（PasswordEncoder + KeyPair）
- `util/JwtTool` — JWT 创建与解析

## 许可证

本项目为学习项目，仅供学习参考。
