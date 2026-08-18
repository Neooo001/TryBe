package com.trybe.material.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p>
 * 素材中心 MQ 配置 — 声明校验队列交换机、队列、绑定关系
 * </p>
 *
 * <p>命名规范：
 * <ul>
 *   <li>交换机：trybe.material.check.exchange（TopicExchange）</li>
 *   <li>队列：material.check.queue</li>
 *   <li>RoutingKey：material.check</li>
 * </ul>
 */
@Configuration
public class MaterialMqConfig {

    public static final String CHECK_EXCHANGE = "trybe.material.check.exchange";
    public static final String CHECK_QUEUE = "material.check.queue";
    public static final String CHECK_ROUTING_KEY = "material.check";

    @Bean
    public TopicExchange materialCheckExchange() {
        return new TopicExchange(CHECK_EXCHANGE, true, false);
    }

    @Bean
    public Queue materialCheckQueue() {
        return QueueBuilder.durable(CHECK_QUEUE).build();
    }

    @Bean
    public Binding materialCheckBinding() {
        return BindingBuilder.bind(materialCheckQueue())
                .to(materialCheckExchange())
                .with(CHECK_ROUTING_KEY);
    }
}
