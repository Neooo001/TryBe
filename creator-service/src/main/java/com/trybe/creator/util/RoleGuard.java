package com.trybe.creator.util;

import cn.hutool.core.util.StrUtil;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.ForbiddenException;
import com.trybe.common.utils.UserContext;
import com.trybe.creator.mapper.UserReadMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 创作者/运营角色守卫
 *
 * <p>JWT 里只有 userId，不含 role；角色的权威来源是 user 表。
 * 读取策略：Redis {@code creator:role:{userId}}（TTL 30min）→ miss 回源只读 user 表 → 回填缓存。
 * 入驻审核由 user-service 通过后主动删除该 key，最坏情况由 30min TTL 兜底。
 *
 * <p>非目标角色一律抛 {@code ForbiddenException(FORBIDDEN, "创作者身份不可用")}（v1 不新增错误码）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleGuard {

    /** 角色缓存 key 前缀（与 user-service CreatorApplyServiceImpl 保持一致） */
    private static final String ROLE_CACHE_KEY_PREFIX = "creator:role:";
    private static final long ROLE_CACHE_TTL_MINUTES = 30L;

    private static final String ROLE_CREATOR = "CREATOR";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String MSG_ROLE_UNAVAILABLE = "创作者身份不可用";

    private final UserReadMapper userReadMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 校验当前登录用户为创作者
     *
     * @return 当前用户ID
     */
    public Long requireCreator() {
        return requireRole(ROLE_CREATOR, MSG_ROLE_UNAVAILABLE);
    }

    /**
     * 校验当前登录用户为运营
     *
     * @return 当前用户ID
     */
    public Long requireAdmin() {
        return requireRole(ROLE_ADMIN, "运营身份不可用");
    }

    /**
     * 通用角色校验
     *
     * @param expectedRole 期望角色
     * @param errorMsg     不匹配时的提示文案
     * @return 当前用户ID
     */
    public Long requireRole(String expectedRole, String errorMsg) {
        Long userId = UserContext.requireUser();
        String role = resolveRole(userId);
        if (!expectedRole.equals(role)) {
            throw new ForbiddenException(ErrorCodeEnum.FORBIDDEN, errorMsg);
        }
        return userId;
    }

    /**
     * 读取当前用户角色（缓存优先，miss 回源 DB）
     *
     * @return 角色字符串；用户不存在时返回 null
     */
    public String resolveRole(Long userId) {
        String cacheKey = ROLE_CACHE_KEY_PREFIX + userId;
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StrUtil.isNotBlank(cached)) {
                return cached;
            }
        } catch (Exception e) {
            log.warn("[RoleGuard] 读取角色缓存失败，回源DB userId={}", userId, e);
        }

        String role = null;
        try {
            role = StrUtil.trimToNull(userReadMapper.selectRoleById(userId));
        } catch (Exception e) {
            log.warn("[RoleGuard] 回源查询角色失败 userId={}", userId, e);
        }

        if (StrUtil.isNotBlank(role)) {
            try {
                stringRedisTemplate.opsForValue().set(cacheKey, role, ROLE_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("[RoleGuard] 回填角色缓存失败 userId={}", userId, e);
            }
        }
        return role;
    }
}
