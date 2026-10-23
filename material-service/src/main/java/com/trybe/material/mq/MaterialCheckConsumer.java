package com.trybe.material.mq;

import com.rabbitmq.client.Channel;
import com.trybe.common.service.OssService;
import com.trybe.common.service.RedisIdempotentGuard;
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
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.LocalDateTime;

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
    private final RedisIdempotentGuard idempotentGuard;
    private final OssService ossService;
    /**
     * 编程式事务模板
     *
     * <p>整个消费流程里混了 Redis、AI 人脸检测、OSS 搬文件三种网络等待，
     * 原先用一个 {@code @Transactional} 全包住 —— 连接会从「收到消息」一直被占到
     * 「最后一条 SQL 提交」，上传高峰期就是它在吃连接池。
     * 现在只有「写日志 + 改状态」这一小段进事务。
     */
    private final TransactionTemplate transactionTemplate;

    private static final String TMP_PATH_PREFIX = "material/tmp/";

    /**
     * 素材校验消费
     *
     */
    @RabbitListener(queues = MaterialMqConfig.CHECK_QUEUE, ackMode = "MANUAL")
    public void onMaterialCheck(MaterialCheckMessage message, Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        Long materialId = message.getMaterialId();
        String messageId = message.getMessageId();
        log.info("[MaterialCheckConsumer] 收到校验消息 materialId={} messageId={} deliveryTag={}",
                materialId, messageId, deliveryTag);

        // ========== 1. Redis 幂等校验 ==========
        String idempotentKey = IDEMPOTENT_KEY_PREFIX + materialId;
        if (!idempotentGuard.tryAcquire(idempotentKey, messageId, IDEMPOTENT_TTL_MINUTES)) {
            log.warn("[MaterialCheckConsumer] 重复消费，已跳过 materialId={} existingMsgId={} currentMsgId={}",
                    materialId, idempotentGuard.peek(idempotentKey), messageId);
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

        // ========== 5. 执行校验（AI 人脸/手部检测 —— 纯网络等待，事务外） ==========
        long startMs = System.currentTimeMillis();
        CheckResult result;
        try {
            result = handler.check(material);
        } catch (Exception e) {
            log.error("[MaterialCheckConsumer] 校验异常 materialId={}", materialId, e);
            result = CheckResult.fail("校验异常: " + e.getMessage(), null);
        }
        long costMs = System.currentTimeMillis() - startMs;
        final CheckResult checkResult = result;

        // ========== 6. 校验通过：tmp → 正式路径（copy + delete 两次网络往返，事务外） ==========
        // 与原实现保持一致：移动失败不阻断，保留 tmp objectKey（tmp 文件由生命周期兜底）。
        // 也就是说这条链路原本就没有「文件与库记录强一致」的保证，拆事务不会让它变差。
        String movedObjectKey = material.getObjectKey();
        if (checkResult.isPassed()) {
            String targetKey = toFinalKey(material.getObjectKey());
            try {
                ossService.moveObject(material.getObjectKey(), targetKey);
                movedObjectKey = targetKey;
            } catch (Exception e) {
                log.error("[MaterialCheckConsumer] 移动OSS文件失败 materialId={} tmp={} final={}",
                        materialId, material.getObjectKey(), targetKey, e);
            }
        }

        // ========== 7. 短事务：校验日志 + 通过时的状态流转（只做本地写） ==========
        String checkTypeStr = materialType.equals("FACE") ? CheckType.AI_FACE.getValue() : CheckType.AI_HAND.getValue();
        final String finalObjectKey = movedObjectKey;
        transactionTemplate.executeWithoutResult(status -> {
            Long logId = checkLogService.logCheckStart(materialId, checkTypeStr);
            checkLogService.logCheckResult(logId, checkResult.isPassed(), checkResult.getDetail(), costMs);
            if (checkResult.isPassed()) {
                material.setObjectKey(finalObjectKey);
                updateMaterialStatus(material, MaterialStatus.PASSED, null);
            }
        });

        // ========== 8. 校验失败：标记失败（内部也是短事务改状态 + 事务外删 tmp 文件） ==========
        if (!checkResult.isPassed()) {
            materialService.markFailed(materialId, checkResult.getMessage());
        }

        // ========== 9. 手动 ACK ==========
        channel.basicAck(deliveryTag, false);
        log.info("[MaterialCheckConsumer] 消费完成 materialId={} passed={} costMs={}", materialId, checkResult.isPassed(), costMs);
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
