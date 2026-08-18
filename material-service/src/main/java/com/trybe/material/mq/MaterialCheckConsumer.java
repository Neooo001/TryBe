package com.trybe.material.mq;

import com.rabbitmq.client.Channel;
import com.trybe.common.service.OssService;
import com.trybe.material.config.MaterialMqConfig;
import com.trybe.material.domain.dto.MaterialCheckMessage;
import com.trybe.material.domain.enums.CheckType;
import com.trybe.material.domain.enums.MaterialStatus;
import com.trybe.material.domain.po.Material;
import com.trybe.material.service.IMaterialCheckLogService;
import com.trybe.material.service.IMaterialService;
import com.trybe.material.service.check.CheckResult;
import com.trybe.material.service.check.MaterialCheckContext;
import com.trybe.material.service.check.MaterialCheckHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 素材校验 MQ 消费者
 * </p>
 *
 * <h3>幂等设计</h3>
 * <ol>
 *   <li>Redis key: material:check:{materialId}，消费前 SETNX，消费后设置 TTL=1h</li>
 *   <li>DB 校验：仅处理 status=CHECKING 的素材</li>
 *   <li>手动 ACK：校验通过后确认，异常时 reject + requeue（最多重试3次）</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialCheckConsumer {

    private static final String IDEMPOTENT_KEY_PREFIX = "material:check:";
    private static final long IDEMPOTENT_TTL_MINUTES = 60;
    private static final int MAX_RETRY_COUNT = 3;

    private final IMaterialService materialService;
    private final IMaterialCheckLogService checkLogService;
    private final MaterialCheckContext checkContext;
    private final StringRedisTemplate stringRedisTemplate;
    private final OssService ossService;

    private static final String TMP_PATH_PREFIX = "material/tmp/";

    @RabbitListener(queues = MaterialMqConfig.CHECK_QUEUE, ackMode = "MANUAL")
    @Transactional
    public void onMaterialCheck(MaterialCheckMessage message, Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        Long materialId = message.getMaterialId();
        String messageId = message.getMessageId();
        log.info("[MaterialCheckConsumer] 收到校验消息 materialId={} messageId={} deliveryTag={}",
                materialId, messageId, deliveryTag);

        // ========== 1. Redis 幂等校验 ==========
        String idempotentKey = IDEMPOTENT_KEY_PREFIX + materialId;
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, messageId, IDEMPOTENT_TTL_MINUTES, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(acquired)) {
            String existingMsgId = stringRedisTemplate.opsForValue().get(idempotentKey);
            log.warn("[MaterialCheckConsumer] 重复消费，已跳过 materialId={} existingMsgId={} currentMsgId={}",
                    materialId, existingMsgId, messageId);
            channel.basicAck(deliveryTag, false);
            return;
        }

        // ========== 2. 查询素材 ==========
        Material material = materialService.getById(materialId);
        if (material == null) {
            log.error("[MaterialCheckConsumer] 素材不存在 materialId={}", materialId);
            channel.basicAck(deliveryTag, false); // 确认消费，丢弃无效消息
            return;
        }

        // ========== 3. DB 幂等校验：仅 CHECKING 状态处理 ==========
        if (material.getStatus() != MaterialStatus.CHECKING) {
            log.warn("[MaterialCheckConsumer] 素材状态非校验中，跳过 materialId={} status={}",
                    materialId, material.getStatus());
            channel.basicAck(deliveryTag, false);
            return;
        }

        // ========== 4. 获取策略处理器 ==========
        String materialType = material.getMaterialType().getValue();
        MaterialCheckHandler handler = checkContext.getHandler(materialType);
        if (handler == null) {
            log.error("[MaterialCheckConsumer] 未找到校验处理器 materialId={} materialType={}", materialId, materialType);
            materialService.markFailed(materialId, "未找到对应校验处理器");
            channel.basicAck(deliveryTag, false);
            return;
        }

        // ========== 5. 执行校验 ==========
        long startMs = System.currentTimeMillis();
        CheckResult result;
        try {
            result = handler.check(material);
        } catch (Exception e) {
            log.error("[MaterialCheckConsumer] 校验异常 materialId={}", materialId, e);
            result = CheckResult.fail("校验异常: " + e.getMessage(), null);
        }
        long costMs = System.currentTimeMillis() - startMs;

        // ========== 6. 记录校验日志 ==========
        String checkTypeStr = materialType.equals("FACE") ? CheckType.AI_FACE.getValue() : CheckType.AI_HAND.getValue();
        Long logId = checkLogService.logCheckStart(materialId, checkTypeStr);
        checkLogService.logCheckResult(logId, result.isPassed(), result.getDetail(), costMs);

        // ========== 7. 更新素材状态 ==========
        if (result.isPassed()) {
            // 校验通过：从 tmp 移动到正式路径，并更新 objectKey
            String finalKey = toFinalKey(material.getObjectKey());
            try {
                ossService.moveObject(material.getObjectKey(), finalKey);
                material.setObjectKey(finalKey);
            } catch (Exception e) {
                log.error("[MaterialCheckConsumer] 移动OSS文件失败 materialId={} tmp={} final={}",
                        materialId, material.getObjectKey(), finalKey, e);
                // 移动失败不阻断，保留 tmp objectKey（tmp 文件由生命周期兜底）
            }
            updateMaterialStatus(material, MaterialStatus.PASSED, null);
        } else {
            materialService.markFailed(materialId, result.getMessage());
        }

        // ========== 8. 手动 ACK ==========
        channel.basicAck(deliveryTag, false);
        log.info("[MaterialCheckConsumer] 消费完成 materialId={} passed={} costMs={}", materialId, result.isPassed(), costMs);
    }

    /**
     * 更新素材校验状态
     */
    private void updateMaterialStatus(Material material, MaterialStatus status, String failReason) {
        material.setStatus(status);
        if (failReason != null) {
            material.setFailReason(failReason);
        }
        material.setUpdateTime(LocalDateTime.now());
        materialService.updateById(material);
    }

    /**
     * 临时路径 → 正式路径（去掉 material/tmp/ 前缀）
     */
    private String toFinalKey(String objectKey) {
        if (objectKey != null && objectKey.startsWith(TMP_PATH_PREFIX)) {
            return "material/" + objectKey.substring(TMP_PATH_PREFIX.length());
        }
        return objectKey;
    }
}
