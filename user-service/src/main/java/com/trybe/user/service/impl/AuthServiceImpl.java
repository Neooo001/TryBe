package com.trybe.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.UnauthorizedException;
import com.trybe.user.config.JwtProperties;
import com.trybe.user.domain.vo.UserLoginVO;
import com.trybe.user.service.AuthService;
import com.trybe.user.util.JwtTool;
import com.trybe.user.util.RefreshTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现 — 双 Token 体系核心逻辑
 *
 * <h3>限流说明</h3>
 * <p>刷新接口限流不在此 Service 层实现，由 {@code AuthController} 通过
 * Guava RateLimiter 或自定义注解控制，保持 Service 纯业务逻辑。
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_BLACKLIST_PREFIX = "token_blacklist:";

    private final JwtTool jwtTool;
    private final JwtProperties jwtProperties;
    private final RefreshTokenUtil refreshTokenUtil;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public UserLoginVO refreshAccessToken(String refreshToken) {
        // 1. 校验 RefreshToken
        if (StrUtil.isBlank(refreshToken)) {
            log.warn("[RefreshToken] 刷新请求中 RefreshToken 为空");
            throw new UnauthorizedException(ErrorCodeEnum.TOKEN_INVALID_RELOGIN, "RefreshToken 不能为空");
        }

        RefreshTokenUtil.TokenPayload payload = refreshTokenUtil.validateToken(refreshToken);
        if (payload == null || payload.getUserId() == null) {
            log.warn("[RefreshToken] 刷新失败：RefreshToken 无效或已过期 mask={}",
                    RefreshTokenUtil.maskToken(refreshToken));
            throw new UnauthorizedException(ErrorCodeEnum.TOKEN_INVALID_RELOGIN, "RefreshToken 已失效，请重新登录");
        }

        // 2. 生成新的 AccessToken
        Duration accessTtl = jwtProperties.getAccessTokenTTL();
        String newAccessToken = jwtTool.createToken(payload.getUserId(), accessTtl);

        // 3. 滚动续期
        refreshTokenUtil.renewToken(refreshToken, payload.getUserId());

        log.info("[RefreshToken] AccessToken 刷新成功 userId={} refreshMask={} newExpiresIn={}s",
                payload.getUserId(),
                RefreshTokenUtil.maskToken(refreshToken),
                accessTtl.getSeconds());

        // 4. 构建返回
        UserLoginVO vo = new UserLoginVO();
        vo.setUserId(payload.getUserId());
        vo.setAccessToken(newAccessToken);
        vo.setExpiresIn(accessTtl.getSeconds());
        // 不返回新的 RefreshToken，前端复用当前
        return vo;
    }

    @Override
    public void logout(Long userId, String accessToken) {
        if (userId == null) {
            log.warn("[Logout] userId 为空，跳过登出");
            return;
        }

        // 1. 删除用户所有 RefreshToken
        refreshTokenUtil.deleteAllTokens(userId);

        // 2. AccessToken 加入黑名单（TTL = 剩余有效期）
        if (StrUtil.isNotBlank(accessToken)) {
            String blacklistKey = TOKEN_BLACKLIST_PREFIX + accessToken;
            long remainingSeconds = getRemainingTTL(accessToken);
            if (remainingSeconds > 0) {
                stringRedisTemplate.opsForValue()
                        .set(blacklistKey, String.valueOf(userId), remainingSeconds, TimeUnit.SECONDS);
                log.info("[Logout] AccessToken 已加入黑名单 userId={} remainingTTL={}s", userId, remainingSeconds);
            }
        }

        log.info("[Logout] userId={} 已登出，所有 RefreshToken 已清除", userId);
    }

    /**
     * 计算 AccessToken 剩余有效时间（秒）
     *
     * <p>失败时返回 0（不做黑名单，避免阻塞登出流程）
     */
    private long getRemainingTTL(String accessToken) {
        try {
            // JwtTool 只暴露了 parseToken 返回 userId，没有直接暴露解析 JWT 的方法
            // 这里通过 hutool 直接解析 ExpiresAt
            cn.hutool.jwt.JWT jwt = cn.hutool.jwt.JWT.of(accessToken);
            Long expiresAt = (Long) jwt.getPayload("exp");
            if (expiresAt != null) {
                long remaining = expiresAt - Instant.now().getEpochSecond();
                return Math.max(remaining, 0);
            }
        } catch (Exception e) {
            log.debug("[Logout] 解析 AccessToken 剩余时间失败，跳过黑名单", e);
        }
        return 0;
    }
}
