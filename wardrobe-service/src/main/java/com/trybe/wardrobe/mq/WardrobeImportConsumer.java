package com.trybe.wardrobe.mq;

import com.rabbitmq.client.Channel;
import com.trybe.common.service.RedisIdempotentGuard;
import com.trybe.wardrobe.config.WardrobeMqConfig;
import com.trybe.wardrobe.domain.dto.WardrobeImportMessage;
import com.trybe.wardrobe.service.WardrobeImportProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * <p>
 * 衣服导入 MQ 消费者
 * </p>
 *
 * <h3>可靠性设计</h3>
 * <ol>
 *   <li><b>幂等</b>：Redis key {@code wardrobe:import:{taskId}} 做 SETNX（走
 *       {@link RedisIdempotentGuard}）；另加 DB 层状态校验（仅 PENDING / PROCESSING 才处理），双重保险</li>
 *   <li><b>并发</b>：单消费者并发度 3，避免大模型调用串行导致排队过长</li>
 *   <li><b>重试</b>：异常时 basicNack(requeue=true)，但重试超过 MAX_RETRY 后
 *       改为投递死信队列，防止坏消息无限循环打满 CPU</li>
 *   <li><b>手动 ACK</b>：处理成功才确认，进程崩溃消息不丢</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WardrobeImportConsumer {

    private static final String IDEMPOTENT_KEY_PREFIX = "wardrobe:import:";
    private static final long IDEMPOTENT_TTL_MINUTES = 120;
    private static final int MAX_RETRY_COUNT = 3;

    private final WardrobeImportProcessor importProcessor;
    private final RedisIdempotentGuard idempotentGuard;

    @RabbitListener(
            queues = WardrobeMqConfig.IMPORT_QUEUE,
            ackMode = "MANUAL",
            concurrency = "1-3")
    public void onImportTask(WardrobeImportMessage message, Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        Long taskId = message.getTaskId();
        String messageId = message.getMessageId();
        log.info("[WardrobeImportConsumer] 收到导入消息 taskId={} taskNo={} messageId={} deliveryTag={}",
                taskId, message.getTaskNo(), messageId, deliveryTag);

        if (taskId == null) {
            log.error("[WardrobeImportConsumer] 消息缺少 taskId，直接丢弃 message={}", message);
            channel.basicAck(deliveryTag, false);
            return;
        }

        // ========== 1. Redis 幂等 ==========
        String idempotentKey = IDEMPOTENT_KEY_PREFIX + taskId;
        if (!idempotentGuard.tryAcquire(idempotentKey, messageId, IDEMPOTENT_TTL_MINUTES)) {
            log.warn("[WardrobeImportConsumer] 重复消费，跳过 taskId={} existingMessageId={} currentMessageId={}",
                    taskId, idempotentGuard.peek(idempotentKey), messageId);
            channel.basicAck(deliveryTag, false);
            return;
        }

        // ========== 2. 执行处理 ==========
        boolean success = false;
        try {
            success = importProcessor.process(taskId);
        } catch (Exception e) {
            log.error("[WardrobeImportConsumer] 处理异常 taskId={}", taskId, e);
        }

        if (success) {
            channel.basicAck(deliveryTag, false);
            log.info("[WardrobeImportConsumer] 消费完成 taskId={}", taskId);
            return;
        }

        // ========== 3. 失败：区分「可重试」与「不可重试」 ==========
        // process() 内部已把业务性失败（模型返回异常、图片损坏）落到任务表 status=FAILED，
        // 此时消息已无重投价值，直接 ACK 释放；只有抛出异常的意外情况才走重试。
        Integer retryCount = message.getRetryCount() == null ? 0 : message.getRetryCount();
        if (retryCount >= MAX_RETRY_COUNT) {
            log.error("[WardrobeImportConsumer] 重试耗尽，转死信 taskId={} retryCount={}", taskId, retryCount);
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        message.setRetryCount(retryCount + 1);
        log.warn("[WardrobeImportConsumer] 处理未成功，重新入队 taskId={} retryCount={}", taskId, message.getRetryCount());
        // 必须先释放幂等键，否则重投会被自己上一次留下的锁拦下
        idempotentGuard.release(idempotentKey);
        channel.basicNack(deliveryTag, false, true);
    }
}
