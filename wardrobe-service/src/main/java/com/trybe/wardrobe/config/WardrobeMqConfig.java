package com.trybe.wardrobe.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p>
 * 衣柜导入 MQ 配置 — 声明衣服导入交换机、队列、绑定关系
 * </p>
 *
 * <p>命名规范（对齐 material-service）：
 * <ul>
 *   <li>交换机：trybe.wardrobe.import.exchange（TopicExchange）</li>
 *   <li>队列：wardrobe.import.queue</li>
 *   <li>RoutingKey：wardrobe.import</li>
 * </ul>
 *
 * <p>死信兜底：处理重试耗尽的消息进入 wardrobe.import.dlq，
 * 由运维侧人工排查，避免坏消息无限 requeue 打满队列。
 */
@Configuration
public class WardrobeMqConfig {

    public static final String IMPORT_EXCHANGE = "trybe.wardrobe.import.exchange";
    public static final String IMPORT_QUEUE = "wardrobe.import.queue";
    public static final String IMPORT_ROUTING_KEY = "wardrobe.import";

    public static final String IMPORT_DLX = "trybe.wardrobe.import.dlx";
    public static final String IMPORT_DLQ = "wardrobe.import.dlq";
    public static final String IMPORT_DLQ_ROUTING_KEY = "wardrobe.import.dead";

    @Bean
    public TopicExchange wardrobeImportExchange() {
        return new TopicExchange(IMPORT_EXCHANGE, true, false);
    }

    @Bean
    public Queue wardrobeImportQueue() {
        return QueueBuilder.durable(IMPORT_QUEUE)
                .deadLetterExchange(IMPORT_DLX)
                .deadLetterRoutingKey(IMPORT_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding wardrobeImportBinding() {
        return BindingBuilder.bind(wardrobeImportQueue())
                .to(wardrobeImportExchange())
                .with(IMPORT_ROUTING_KEY);
    }

    @Bean
    public TopicExchange wardrobeImportDlx() {
        return new TopicExchange(IMPORT_DLX, true, false);
    }

    @Bean
    public Queue wardrobeImportDlq() {
        return QueueBuilder.durable(IMPORT_DLQ).build();
    }

    @Bean
    public Binding wardrobeImportDlqBinding() {
        return BindingBuilder.bind(wardrobeImportDlq())
                .to(wardrobeImportDlx())
                .with(IMPORT_DLQ_ROUTING_KEY);
    }
}
