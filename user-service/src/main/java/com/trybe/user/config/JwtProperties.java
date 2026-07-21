package com.trybe.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;

/**
 * JWT 配置属性 — 双 Token 体系
 *
 * <p>accessTokenTTL 控制 AccessToken（JWT）有效期，默认 30 分钟。
 * refreshTokenTTL 控制 RefreshToken（随机字符串）在 Redis 中的有效期，默认 30 天。
 *
 * @author TryBe Team
 */
@Data
@ConfigurationProperties(prefix = "hm.jwt")
public class JwtProperties {
    private Resource location;
    private String password;
    private String alias;

    /** @deprecated 已拆分为 accessTokenTTL / refreshTokenTTL，保留以兼容旧 nacos 配置 */
    @Deprecated
    private Duration tokenTTL = Duration.ofMinutes(10);

    /** AccessToken（JWT）有效期，默认 30 分钟 */
    private Duration accessTokenTTL = Duration.ofMinutes(30);

    /** RefreshToken Redis TTL，默认 30 天 */
    private Duration refreshTokenTTL = Duration.ofDays(30);

    /**
     * 获取 AccessToken 有效期。
     * 优先使用 accessTokenTTL，未配置时回退到旧 tokenTTL。
     */
    public Duration getAccessTokenTTL() {
        return accessTokenTTL != null ? accessTokenTTL : tokenTTL;
    }

    /**
     * 获取 RefreshToken 有效期。
     */
    public Duration getRefreshTokenTTL() {
        return refreshTokenTTL != null ? refreshTokenTTL : Duration.ofDays(30);
    }
}
