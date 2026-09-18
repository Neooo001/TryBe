package com.trybe.creator.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 创作者物品业务编号生成器
 *
 * <p>规则：{@code CI + yyyyMMdd + 当日 Redis INCR 序列补零 6 位}，如 CI20250101000001。
 * 序列 key：{@code creator:itemno:yyyyMMdd}，TTL 48h（跨天自动换新 key，48h 兜底防残留）。
 *
 * <p>依赖 Redis INCR 的原子性保证并发下序列唯一；DB 层另有 uk_item_no 唯一索引兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItemNoGenerator {

    private static final String KEY_PREFIX = "creator:itemno:";
    private static final long SEQ_KEY_TTL_HOURS = 48L;
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 生成下一个物品业务编号
     *
     * @return 形如 CI20250101000001 的业务编号
     */
    public String nextItemNo() {
        String day = LocalDate.now().format(DAY_FORMAT);
        String seqKey = KEY_PREFIX + day;
        Long seq = stringRedisTemplate.opsForValue().increment(seqKey);
        if (seq == null) {
            // INCR 返回 null 仅发生在 Redis 异常时，此时退化用时间戳尾数，仍有 uk_item_no 兜底
            log.warn("[ItemNoGenerator] Redis INCR 返回 null，退化为时间戳序列 day={}", day);
            seq = System.currentTimeMillis() % 1_000_000L;
        } else if (seq == 1L) {
            // 当日首次使用，设置 TTL
            stringRedisTemplate.expire(seqKey, SEQ_KEY_TTL_HOURS, TimeUnit.HOURS);
        }
        return String.format("CI%s%06d", day, seq);
    }
}
