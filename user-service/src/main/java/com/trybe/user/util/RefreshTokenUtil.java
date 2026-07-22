package com.trybe.user.util;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * RefreshToken 工具类 — 对标抖音长效登录机制
 *
 * <h3>核心规则</h3>
 * <ul>
 *   <li>RefreshToken 为 32 位安全随机字符串（非 JWT），仅用于刷新 AccessToken</li>
 *   <li>基础有效期 {@value #REFRESH_TOKEN_TTL_DAYS} 天，每次刷新自动重置 TTL（滚动续期）</li>
 *   <li>单用户最多 {@value #MAX_DEVICE_COUNT} 台设备同时在线，新登录淘汰最早会话</li>
 *   <li>Redis 存储两套 Key：</li>
 *   <ul>
 *     <li>{@code refresh_token:{token}} — Hash 存 userId、openid、createTime，TTL 7 天</li>
 *     <li>{@code user_tokens:{userId}} — Set 存该用户所有 refreshToken，用于多设备管控</li>
 *   </ul>
 * </ul>
 *
 * <h3>Token 脱敏规则</h3>
 * <p>日志格式化时始终截取前 6 位 + 后 4 位，中间用 *** 替代</p>
 *
 * @author TryBe Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenUtil {

    /** RefreshToken 长度（字节），hex 编码后为 64 字符 */
    private static final int TOKEN_BYTE_LENGTH = 32;

    /** RefreshToken 基础有效期（天） */
    static final int REFRESH_TOKEN_TTL_DAYS = 30;

    /** 单用户最大设备数 */
    private static final int MAX_DEVICE_COUNT = 1;

    /** Redis Key 前缀 */
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";

    /** 用户 Token 集合 Key 前缀 */
    private static final String USER_TOKENS_PREFIX = "user_tokens:";

    private final StringRedisTemplate stringRedisTemplate;

    // ==================== Token 生成 ====================

    /**
     * 生成 32 位安全随机字符串作为 RefreshToken（hex 编码，64 字符）
     *
     * @return 随机 RefreshToken
     */
    public String generateToken() {
        return RandomUtil.randomString(TOKEN_BYTE_LENGTH * 2);
    }

    // ==================== Token 存储 ====================

    /**
     * 保存 RefreshToken 到 Redis
     *
     * <p>自动执行多设备管控：若用户已有 {@link #MAX_DEVICE_COUNT} 台设备在线，
     * 删除创建时间最早的 RefreshToken
     *
     * @param refreshToken RefreshToken 字符串
     * @param userId       用户ID
     * @param openid       微信 openid（用于审计日志）
     */
    public void saveToken(String refreshToken, Long userId, String openid) {
        // 1. 多设备管控：检查当前设备数
        String userTokensKey = buildUserTokensKey(userId);
        Set<String> existingTokens = stringRedisTemplate.opsForSet().members(userTokensKey);
        if (existingTokens != null && existingTokens.size() >= MAX_DEVICE_COUNT) {
            // 淘汰最早创建的 RefreshToken（按创建时间排序，删除最早的）
            String oldestToken = null;
            long oldestTime = Long.MAX_VALUE;
            for (String token : existingTokens) {
                String tokenKey = buildTokenKey(token);
                String createTimeStr = (String) stringRedisTemplate.opsForHash().get(tokenKey, "createTime");
                if (createTimeStr != null) {
                    long createTime = Long.parseLong(createTimeStr);
                    if (createTime < oldestTime) {
                        oldestTime = createTime;
                        oldestToken = token;
                    }
                }
            }
            if (oldestToken != null) {
                deleteToken(oldestToken, userId);
                log.info("[RefreshToken] 设备数超限，淘汰最早会话 userId={} oldDeviceMask={}",
                        userId, maskToken(oldestToken));
            }
        }

        // 2. 存储 RefreshToken → 用户信息
        long now = System.currentTimeMillis();
        String tokenKey = buildTokenKey(refreshToken);
        stringRedisTemplate.opsForHash().put(tokenKey, "userId", String.valueOf(userId));
        stringRedisTemplate.opsForHash().put(tokenKey, "openid", openid);
        stringRedisTemplate.opsForHash().put(tokenKey, "createTime", String.valueOf(now));
        stringRedisTemplate.expire(tokenKey, REFRESH_TOKEN_TTL_DAYS, TimeUnit.DAYS);

        // 3. 维护用户 → Token 集合
        stringRedisTemplate.opsForSet().add(userTokensKey, refreshToken);
        stringRedisTemplate.expire(userTokensKey, REFRESH_TOKEN_TTL_DAYS, TimeUnit.DAYS);

        log.info("[RefreshToken] 已保存 userId={} mask={} deviceCount={}",
                userId, maskToken(refreshToken),
                (existingTokens != null ? existingTokens.size() : 0) + 1);
    }

    // ==================== Token 校验 ====================

    /**
     * 校验 RefreshToken 并返回用户信息
     *
     * @param refreshToken RefreshToken 字符串
     * @return TokenPayload 含 userId + openid；Token 不存在或已过期返回 null
     */
    public TokenPayload validateToken(String refreshToken) {
        if (StrUtil.isBlank(refreshToken)) {
            log.debug("[RefreshToken] 校验失败：token 为空");
            return null;
        }

        String tokenKey = buildTokenKey(refreshToken);
        String userIdStr = (String) stringRedisTemplate.opsForHash().get(tokenKey, "userId");
        if (userIdStr == null) {
            log.debug("[RefreshToken] 校验失败：token 不存在 mask={}", maskToken(refreshToken));
            return null;
        }

        String openid = (String) stringRedisTemplate.opsForHash().get(tokenKey, "openid");
        String createTimeStr = (String) stringRedisTemplate.opsForHash().get(tokenKey, "createTime");
        long userId = Long.parseLong(userIdStr);
        long createTime = createTimeStr != null ? Long.parseLong(createTimeStr) : 0L;

        TokenPayload payload = new TokenPayload();
        payload.setUserId(userId);
        payload.setOpenid(openid);
        payload.setCreateTime(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(createTime), ZoneId.systemDefault()));
        payload.setRefreshToken(refreshToken);

        return payload;
    }

    /**
     * 刷新后校验并返回用户信息（仅 userId，精简版）
     *
     * @param refreshToken RefreshToken 字符串
     * @return userId；Token 无效返回 null
     */
    public Long validateAndGetUserId(String refreshToken) {
        TokenPayload payload = validateToken(refreshToken);
        return payload != null ? payload.getUserId() : null;
    }

    // ==================== 滚动续期 ====================

    /**
     * 滚动续期 — 重置 RefreshToken 的 Redis TTL 为满 30 天
     *
     * <p>对标抖音：用户每次成功刷新 AccessToken，RefreshToken 自动续命，
     * 只要持续使用小程序就永不过期。
     *
     * @param refreshToken RefreshToken 字符串
     * @param userId       用户ID（由调用方提供，避免反查 Redis）
     */
    public void renewToken(String refreshToken, Long userId) {
        if (StrUtil.isBlank(refreshToken)) {
            return;
        }
        String tokenKey = buildTokenKey(refreshToken);
        String userTokensKey = buildUserTokensKey(userId);

        Boolean renewed = stringRedisTemplate.expire(tokenKey, REFRESH_TOKEN_TTL_DAYS, TimeUnit.DAYS);
        if (Boolean.TRUE.equals(renewed)) {
            // 同步续期用户 Token 集合
            stringRedisTemplate.expire(userTokensKey, REFRESH_TOKEN_TTL_DAYS, TimeUnit.DAYS);
            log.debug("[RefreshToken] 滚动续期成功 userId={} mask={}", userId, maskToken(refreshToken));
        }
    }

    // ==================== Token 删除 ====================

    /**
     * 删除单条 RefreshToken（登出单个设备）
     *
     * @param refreshToken RefreshToken 字符串
     * @param userId       用户ID（用于清理 user_tokens 集合）
     */
    public void deleteToken(String refreshToken, Long userId) {
        if (StrUtil.isBlank(refreshToken)) {
            return;
        }
        String tokenKey = buildTokenKey(refreshToken);
        stringRedisTemplate.delete(tokenKey);

        if (userId != null) {
            stringRedisTemplate.opsForSet().remove(buildUserTokensKey(userId), refreshToken);
        }

        log.info("[RefreshToken] 已删除单条 token userId={} mask={}", userId, maskToken(refreshToken));
    }

    /**
     * 删除用户的所有 RefreshToken（全设备强制下线 / 主动登出）
     *
     * @param userId 用户ID
     */
    public void deleteAllTokens(Long userId) {
        if (userId == null) {
            return;
        }
        String userTokensKey = buildUserTokensKey(userId);
        Set<String> tokens = stringRedisTemplate.opsForSet().members(userTokensKey);
        if (tokens != null && !tokens.isEmpty()) {
            for (String token : tokens) {
                stringRedisTemplate.delete(buildTokenKey(token));
            }
        }
        stringRedisTemplate.delete(userTokensKey);

        log.info("[RefreshToken] 已删除用户全部 token userId={} count={}",
                userId, tokens != null ? tokens.size() : 0);
    }

    // ==================== Redis Key 构建 ====================

    private String buildTokenKey(String refreshToken) {
        return REFRESH_TOKEN_PREFIX + refreshToken;
    }

    private String buildUserTokensKey(Long userId) {
        return USER_TOKENS_PREFIX + userId;
    }

    // ==================== 日志工具 ====================

    /**
     * Token 脱敏：仅显示前 6 位 + 后 4 位
     */
    public static String maskToken(String token) {
        if (StrUtil.isBlank(token) || token.length() <= 10) {
            return "***";
        }
        return token.substring(0, 6) + "***" + token.substring(token.length() - 4);
    }

    // ==================== 内嵌类 ====================

    /**
     * RefreshToken 在 Redis 中存储的负载信息
     */
    @lombok.Data
    public static class TokenPayload {
        private Long userId;
        private String openid;
        private LocalDateTime createTime;
        private String refreshToken;
    }
}
