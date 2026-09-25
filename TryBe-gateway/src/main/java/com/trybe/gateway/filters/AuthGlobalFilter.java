package com.trybe.gateway.filters;

import com.trybe.common.auth.RealmConstants;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.UnauthorizedException;
import com.trybe.gateway.config.AuthProperties;
import com.trybe.gateway.util.JwtTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 全局鉴权过滤器 — 双 Token 体系
 *
 * <h3>核心改动（相对旧版本）</h3>
 * <ol>
 *   <li>使用 {@link JwtTool#parseTokenWithExpire(String)} 区分过期 vs 非法</li>
 *   <li>AccessToken 过期 → {@code 40101}（前端自动刷新）</li>
 *   <li>Token 非法 / 签名错误 → {@code 40102}（前端引导重登）</li>
 *   <li>增加 AccessToken 黑名单校验（登出后立即失效）</li>
 *   <li>结构化日志：记录请求路径、用户标识、失败原因，Token 脱敏</li>
 * </ol>
 *
 * @author TryBe Team
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String TOKEN_BLACKLIST_PREFIX = "token_blacklist:";

    private final AuthProperties authProperties;
    private final JwtTool jwtTool;
    private final StringRedisTemplate stringRedisTemplate;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().toString();

        // 1. 白名单放行
        if (isExclude(path)) {
            return chain.filter(exchange);
        }

        // 2. 提取 AccessToken
        String token = extractToken(request);
        if (token == null) {
            return writeAuthError(exchange, ErrorCodeEnum.TOKEN_INVALID_RELOGIN,
                    "未携带登录凭证", path, "anonymous", "token_missing");
        }

        // 3. 黑名单校验
        String blacklistKey = TOKEN_BLACKLIST_PREFIX + token;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(blacklistKey))) {
            log.info("[Auth] Token 命中黑名单 path={} tokenMask={}", path, maskToken(token));
            return writeAuthError(exchange, ErrorCodeEnum.TOKEN_INVALID_RELOGIN,
                    "该凭证已被注销，请重新登录", path, "anonymous", "blacklist");
        }

        // 4. 校验 Token（区分过期 vs 非法）
        JwtTool.ParsedToken parsed;
        try {
            parsed = jwtTool.parseTokenWithExpire(token);
        } catch (UnauthorizedException e) {
            ErrorCodeEnum errorCode = ErrorCodeEnum.TOKEN_INVALID_RELOGIN; // 兜底
            if (e.getCode() == ErrorCodeEnum.ACCESS_TOKEN_EXPIRE.getCode()) {
                errorCode = ErrorCodeEnum.ACCESS_TOKEN_EXPIRE;
            }
            String reason = errorCode == ErrorCodeEnum.ACCESS_TOKEN_EXPIRE ? "expired" : "invalid";
            log.warn("[Auth] Token 校验失败 path={} tokenMask={} reason={} msg={}",
                    path, maskToken(token), reason, e.getMessage());
            return writeAuthError(exchange, errorCode,
                    e.getMessage(), path, "anonymous", reason);
        } catch (Exception e) {
            log.error("[Auth] Token 解析异常 path={} tokenMask={}", path, maskToken(token), e);
            return writeAuthError(exchange, ErrorCodeEnum.TOKEN_INVALID_RELOGIN,
                    "登录凭证异常", path, "anonymous", "parse_error");
        }

        Long userId = parsed.getUserId();
        String realm = parsed.getRealm();

        // 4.5 账号域校验：A 域 token 不得访问 B 域接口
        //     userId 只在域内唯一（wx 域 1 号 ≠ web 域 1 号），跨域即越权访问他人数据
        //     realm=any 的路径（/auth/**）为跨域共享，只要求令牌合法，不做域匹配
        String expectedRealm = matchRealm(path);
        boolean realmAllowed = expectedRealm != null
                && (RealmConstants.ANY.equals(expectedRealm) || expectedRealm.equals(realm));
        if (!realmAllowed) {
            log.warn("[Auth] 跨域访问被拒 path={} tokenRealm={} expectedRealm={} userId={}",
                    path, realm, expectedRealm, userId);
            return writeAuthError(exchange, ErrorCodeEnum.CROSS_REALM_FORBIDDEN,
                    "无权访问该端接口，请重新登录", path, String.valueOf(userId), "realm_mismatch");
        }

        // 5. 鉴权通过，传递 user-info + user-realm
        String userInfo = userId.toString();
        ServerWebExchange swe = exchange.mutate()
                .request(builder -> builder.header("user-info", userInfo)
                        .header(RealmConstants.HEADER_USER_REALM, realm))
                .build();

        log.debug("[Auth] 鉴权通过 userId={} realm={} path={}", userId, realm, path);
        return chain.filter(swe);
    }

    // ==================== 工具方法 ====================

    /**
     * 从请求头提取 Bearer Token
     */
    private String extractToken(ServerHttpRequest request) {
        List<String> headers = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (headers != null && !headers.isEmpty()) {
            String token = headers.get(0);
            if (token != null && token.startsWith("Bearer ")) {
                return token.substring(7);
            }
        }
        return null;
    }

    /**
     * 统一鉴权失败响应（HTTP 200 + JSON body）
     */
    private Mono<Void> writeAuthError(ServerWebExchange exchange, ErrorCodeEnum errorCode,
                                       String msg, String path, String userId, String reason) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"code\":%d,\"msg\":\"%s\",\"data\":null}",
                errorCode.getCode(), msg);

        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 匹配请求路径所属的账号域
     *
     * <p>遍历 {@code hm.auth.realm-paths} 配置（realm → 路径模式列表），
     * 命中则返回该 realm；未命中任何模式返回 null（由调用方判定为跨域拒绝）。
     * 命中 {@link RealmConstants#ANY} 表示该路径跨域共享，调用方跳过域相等性校验。
     *
     * <p><b>未配置 realm-paths 时的行为</b>：返回 null → 所有已鉴权请求都会被拒。
     * 这是刻意设计的 fail-closed：宁可拒绝，也不放行无域约束的跨域访问。
     *
     * @param path 请求路径
     * @return 期望的账号域；未命中返回 null
     */
    private String matchRealm(String path) {
        java.util.Map<String, List<String>> realmPaths = authProperties.getRealmPaths();
        if (realmPaths == null || realmPaths.isEmpty()) {
            log.error("[Auth] 未配置 hm.auth.realm-paths，拒绝所有鉴权请求 path={}", path);
            return null;
        }
        for (java.util.Map.Entry<String, List<String>> entry : realmPaths.entrySet()) {
            List<String> patterns = entry.getValue();
            if (patterns == null) {
                continue;
            }
            for (String pattern : patterns) {
                if (antPathMatcher.match(pattern, path)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private boolean isExclude(String path) {
        for (String pathPattern : authProperties.getExcludePaths()) {
            if (antPathMatcher.match(pathPattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Token 脱敏：仅显示前 8 位 + 后 4 位
     */
    private String maskToken(String token) {
        if (token == null || token.length() <= 12) {
            return "***";
        }
        return token.substring(0, 8) + "***" + token.substring(token.length() - 4);
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
