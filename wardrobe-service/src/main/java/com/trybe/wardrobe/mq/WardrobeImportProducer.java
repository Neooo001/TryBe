package com.trybe.wardrobe.mq;

import cn.hutool.core.util.IdUtil;
import com.trybe.wardrobe.config.WardrobeMqConfig;
import com.trybe.wardrobe.domain.dto.WardrobeImportMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 衣服导入消息生产者
 *
 * <p>发送失败不阻断主流程：任务已落库为 PENDING，可由定时补偿任务扫描重投，
 * 也能被用户在「重新识别」入口手动触发，避免 MQ 抖动直接导致导入失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WardrobeImportProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 投递导入任务消息
     *
     * @return 是否投递成功
     */
    public boolean sendImportTask(WardrobeImportMessage message) {
        if (message.getMessageId() == null) {
            message.setMessageId(IdUtil.fastSimpleUUID());
        }
        try {
            rabbitTemplate.convertAndSend(
                    WardrobeMqConfig.IMPORT_EXCHANGE,
                    WardrobeMqConfig.IMPORT_ROUTING_KEY,
                    message);
            log.info("[WardrobeImport] 消息投递成功 taskId={} taskNo={} messageId={}",
                    message.getTaskId(), message.getTaskNo(), message.getMessageId());
            return true;
        } catch (Exception e) {
            log.error("[WardrobeImport] 消息投递失败 taskId={} taskNo={}",
                    message.getTaskId(), message.getTaskNo(), e);
            return false;
        }
    }
}
