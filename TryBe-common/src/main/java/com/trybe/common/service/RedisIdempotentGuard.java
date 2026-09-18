package com.trybe.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * <p>
 * Redis 幂等守卫 —— 消费端防重复处理
 * </p>
 *
 * <p>消息队列是「至少投递一次」（at-least-once）语义：网络抖动重投、
 * 消费者重启、手动 requeue、业务重试，都会造成同一条消息被消费多次。
 * 各服务的消费逻辑里都散落着同一段 SETNX 代码，且 TTL、空值兜底、
 * 释放时机各有细微差异 —— 这类差异正是「偶发重复扣减 / 重复建单」的根源。
 *
 * <p>本类把这段语义收敛为唯一实现：<b>谁 SETNX 成功，谁获得处理权</b>。
 *
 * <p>使用姿势（典型是 try/finally 释放的变体：失败重投前必须 release）：
 * <pre>
 * if (!guard.tryAcquire(key, messageId, 120)) {
 *     channel.basicAck(deliveryTag, false);   // 已处理过，直接确认
 *     return;
 * }
 * try {
 *     ...业务处理...
 * } catch (Exception e) {
 *     guard.release(key);                     // 一定要释放，否则重投会被自己拦下
 *     throw e;
 * }
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(StringRedisTemplate.class)
public class RedisIdempotentGuard {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 尝试获取处理权
     *
     * @param key        幂等键，建议命名 {@code 业务域:动作:{业务ID}}，如 {@code wardrobe:import:1024}
     * @param value      幂等值，通常传消息ID，便于排查重复来源
     * @param ttlMinutes 过期时间（分钟）。需大于「业务最长处理时间」，否则会失去幂等保护
     * @return true 表示本次获得处理权；false 表示已被处理过，调用方应直接 ACK 丢弃
     */
    public boolean tryAcquire(String key, String value, long ttlMinutes) {
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, value == null ? "1" : value, ttlMinutes, TimeUnit.MINUTES);
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * 释放处理权
     *
     * <p>仅在「消息将要被重投」时调用：否则同一个 taskId 的重投消息
     * 会被自己上一次留下的幂等键拦下，表现为「重试永远不生效」。
     */
    public void release(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("[IdempotentGuard] 释放幂等键失败 key={}", key, e);
        }
    }

    /**
     * 读取当前幂等值（消息ID），用于日志排查「重复消息来自哪一次投递」
     */
    public String peek(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("[IdempotentGuard] 读取幂等键失败 key={}", key, e);
            return null;
        }
    }
}
