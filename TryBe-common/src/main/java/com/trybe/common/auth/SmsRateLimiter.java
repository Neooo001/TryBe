package com.trybe.common.auth;

import cn.hutool.core.util.StrUtil;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BizIllegalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 短信发送限流 —— 频率维度防线，防止验证码被当成短信轰炸工具。
 *
 * <p>两个发码入口共用本类，只是「第一道防线」不同：
 * <ul>
 *   <li>{@code POST /users/auth/sms/code}（免鉴权）—— 第一道防线是<b>图形验证码</b>；</li>
 *   <li>{@code POST /users/auth/sms/code/authed}（需登录）—— 第一道防线是<b>登录态</b>。</li>
 * </ul>
 * 两者频率维度完全一致，都走 {@link #check(String)}。
 *
 * <p><b>限流维度：只有手机号一个</b>（刻意不设 IP / 全局 / 用户级）：
 * <ul>
 *   <li>{@code sms:day:phone:{phone}:{yyyyMMdd}} —— 单手机号每日上限 <b>100</b> 条</li>
 * </ul>
 *
 * <p>60 秒发送间隔由 {@code SmsService} 内部的 {@code sms:limit:{phone}} 负责，此处不重复实现。
 *
 * <p><b>模块位置</b>：位于 {@code TryBe-common}，仅依赖 Spring Data Redis 与 common 自身的
 * {@code ErrorCodeEnum}/{@code BizIllegalException}，不含任何 {@code com.trybe.user.*} 依赖。
 *
 * @author TryBe Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsRateLimiter {

    private static final String DAY_PHONE_PREFIX = "sms:day:phone:";

    private static final long MAX_PER_PHONE_PER_DAY = 100L;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 校验发送资格，超限直接抛业务异常。
     *
     * <p>超限以 {@link ErrorCodeEnum#SMS_TOO_FREQUENT}（406）抛出，前端据此展示可读提示。
     */
    public void check(String phone) {
        String day = LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);

        checkAndIncr(DAY_PHONE_PREFIX + phone + ":" + day, MAX_PER_PHONE_PER_DAY, "该手机号今日验证码发送已达上限");
    }

    private void checkAndIncr(String key, long max, String errorMsg) {
        Long current = stringRedisTemplate.opsForValue().increment(key);
        if (current != null && current == 1L) {
            // 首次计数时设置到当日 24 点过期
            stringRedisTemplate.expireAt(key, endOfDay());
        }
        if (current != null && current > max) {
            log.warn("[sms-limit] 触发限流 key={} current={} max={}", key, current, max);
            throw new BizIllegalException(ErrorCodeEnum.SMS_TOO_FREQUENT, errorMsg);
        }
    }

    /** 客户端 IP 归一化：取 X-Forwarded-For 首段，缺失时归为 unknown */
    public static String normalizeIp(String forwardedFor) {
        if (StrUtil.isBlank(forwardedFor)) {
            return "unknown";
        }
        return forwardedFor.split(",")[0].trim();
    }

    private Date endOfDay() {
        LocalDateTime tomorrowStart = LocalDate.now().plusDays(1).atStartOfDay();
        return Date.from(tomorrowStart.atZone(ZoneId.systemDefault()).toInstant());
    }
}