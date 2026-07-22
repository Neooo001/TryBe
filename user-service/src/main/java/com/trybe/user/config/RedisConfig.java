package com.trybe.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置 — 统一序列化规则
 *
 * <p>自定义 RedisTemplate 解决默认 JDK 序列化可读性差的问题：
 * <ul>
 *   <li>Key 序列化：{@link StringRedisSerializer}</li>
 *   <li>Value 序列化：{@link GenericJackson2JsonRedisSerializer}</li>
 * </ul>
 *
 * <p>同时暴露 {@link StringRedisTemplate}，RefreshToken 的简单 String 读写
 * 直接使用 StringRedisTemplate 即可，无需 JSON 序列化。
 *
 * @author TryBe Team
 */
@Configuration
public class RedisConfig {

    /**
     * 泛型 RedisTemplate — 适用于复杂对象缓存（如用户资料、会话信息等）
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Key 统一用 String 序列化
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value 用 JSON 序列化，便于人工排查和跨语言解析
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * String 专用 RedisTemplate — RefreshToken 等简单 K-V 场景无需 JSON 序列化
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
