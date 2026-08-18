package com.trybe.user.service;

import cn.hutool.core.util.StrUtil;
import com.trybe.common.config.OssProperties;
import com.trybe.common.service.OssService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarUrlService {

    private static final String AVATAR_URL_KEY_PREFIX = "avatar:presigned:";

    private static final long REDIS_TTL_BUFFER_SECONDS = 60L;

    private final StringRedisTemplate stringRedisTemplate;
    private final OssService ossService;
    private final OssProperties ossProperties;

    /**
     * 获取用户头像的预签名URL（优先从Redis缓存获取，过期自动重新生成）
     *
     * @param userId          用户ID
     * @param avatarObjectKey 用户头像在数据库中存储的值（objectKey或完整URL）
     * @return 预签名URL，如果头像为空则返回null
     */
    public String getPresignedAvatarUrl(Long userId, String avatarObjectKey) {
        if (StrUtil.isBlank(avatarObjectKey)) {
            return null;
        }

        String redisKey = AVATAR_URL_KEY_PREFIX + userId;

        String cachedUrl = stringRedisTemplate.opsForValue().get(redisKey);
        if (cachedUrl != null) {
            Long remainSeconds = stringRedisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
            if (remainSeconds != null && remainSeconds > REDIS_TTL_BUFFER_SECONDS) {
                log.debug("命中头像预签名URL缓存: userId={}, remainSeconds={}", userId, remainSeconds);
                return cachedUrl;
            }
            log.info("头像预签名URL缓存即将过期，重新生成: userId={}, remainSeconds={}", userId, remainSeconds);
        }

        String objectKey = extractObjectKey(avatarObjectKey);
        long expireSeconds = getExpireSeconds();
        String presignedUrl = ossService.generatePresignedUrl(objectKey, expireSeconds);

        long redisTtlSeconds = Math.max(expireSeconds - REDIS_TTL_BUFFER_SECONDS, REDIS_TTL_BUFFER_SECONDS);
        stringRedisTemplate.opsForValue().set(redisKey, presignedUrl, Duration.ofSeconds(redisTtlSeconds));
        log.info("生成并缓存头像预签名URL: userId={}, ossExpireSeconds={}, redisTtlSeconds={}", userId, expireSeconds, redisTtlSeconds);

        return presignedUrl;
    }

    /**
     * 使用户头像预签名URL缓存失效（头像更新时调用）
     *
     * @param userId 用户ID
     */
    public void invalidateCache(Long userId) {
        stringRedisTemplate.delete(AVATAR_URL_KEY_PREFIX + userId);
        log.info("清除头像预签名URL缓存: userId={}", userId);
    }

    /**
     * 从avatar字段值中提取OSS objectKey
     * 兼容两种存储格式：纯objectKey 或 完整URL
     *
     * @param avatar 数据库中存储的avatar值
     * @return OSS objectKey
     */
    private String extractObjectKey(String avatar) {
        if (!avatar.startsWith("http")) {
            return avatar;
        }
        try {
            URL url = new URL(avatar);
            String path = url.getPath();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            return path;
        } catch (MalformedURLException e) {
            log.warn("无法解析头像URL，将作为objectKey使用: {}", avatar, e);
            return avatar;
        }
    }

    private long getExpireSeconds() {
        return ossProperties.getExpireSeconds() != null ? ossProperties.getExpireSeconds() : 3600L;
    }
}