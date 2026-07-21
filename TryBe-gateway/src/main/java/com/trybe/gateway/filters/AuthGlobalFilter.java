package com.trybe.gateway.filters;

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
        Long userId;
        try {
            userId = jwtTool.parseTokenWithExpire(token);
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

        // 5. 鉴权通过，传递 user-info
        String userInfo = userId.toString();
        ServerWebExchange swe = exchange.mutate()
                .request(builder -> builder.header("user-info", userInfo))
                .build();

        log.debug("[Auth] 鉴权通过 userId={} path={}", userId, path);
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
