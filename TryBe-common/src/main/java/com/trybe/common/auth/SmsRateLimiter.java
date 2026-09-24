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
 * 短信发送限流（免鉴权发码接口的第二道防线）
 *
 * <p>第一道防线是图形验证码；本类负责频率维度，防止验证码被当成短信轰炸工具：
 * <ul>
 *   <li>{@code sms:day:phone:{phone}:{yyyyMMdd}} —— 单手机号每日上限 10 条</li>
 *   <li>{@code sms:day:ip:{ip}:{yyyyMMdd}} —— 单 IP 每日上限 30 条</li>
 *   <li>{@code sms:day:global:{yyyyMMdd}} —— 全局熔断 200 条（防止手滑/脚本刷爆短信账单）</li>
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
    private static final String DAY_IP_PREFIX = "sms:day:ip:";
    private static final String DAY_GLOBAL_PREFIX = "sms:day:global:";

    private static final long MAX_PER_PHONE_PER_DAY = 10L;
    private static final long MAX_PER_IP_PER_DAY = 30L;
    private static final long MAX_GLOBAL_PER_DAY = 200L;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 校验发送资格，超限直接抛业务异常。
     *
     * <p>三个计数器都要自增（而非短路返回），否则攻击者可通过切换手机号绕过 IP 维度统计。
     * 超限统一以 {@link ErrorCodeEnum#SMS_TOO_FREQUENT}（406）抛出，前端据此展示可读提示。
     */
    public void check(String phone, String clientIp) {
        String day = LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);

        checkAndIncr(DAY_GLOBAL_PREFIX + day, MAX_GLOBAL_PER_DAY, "今日验证码发送量已达上限，请明日再试");
        checkAndIncr(DAY_IP_PREFIX + clientIp + ":" + day, MAX_PER_IP_PER_DAY, "操作过于频繁，请稍后再试");
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
