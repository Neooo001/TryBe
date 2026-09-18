package com.trybe.wardrobe.service;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.trybe.common.service.OssService;
import com.trybe.common.utils.FileTypeUtils;
import com.trybe.wardrobe.config.GarmentAiProperties;
import com.trybe.wardrobe.domain.dto.GarmentRecognizeResult;
import com.trybe.wardrobe.domain.enums.ImportTaskStatus;
import com.trybe.wardrobe.domain.po.WardrobeImportTask;
import com.trybe.wardrobe.mapper.WardrobeImportTaskMapper;
import com.trybe.wardrobe.service.ai.GarmentImageCropService;
import com.trybe.wardrobe.service.ai.GarmentMattingService;
import com.trybe.wardrobe.service.ai.GarmentRecognizeService;
import com.trybe.wardrobe.util.WardrobeOssPathBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;

/**
 * 衣服导入任务处理器 —— 异步任务的实际执行体
 *
 * <h3>处理流水线</h3>
 * <pre>
 * 1. 加载任务，状态守卫（仅 PENDING / PROCESSING 处理，天然幂等）
 * 2. 标记 PROCESSING + startTime
 * 3. 从 OSS 拉原图 → 必要时压缩
 * 4. 调用视觉大模型 → 结构化属性 + 主体框 bbox
 * 5. 先试「像素级抠图」，不行退回「bbox 矩形裁剪」，都不行用原图；产物只写 task.cutout_object_key
 * 6. 任务置 SUCCESS/FAILED，回填 aiResultJson / cutoutObjectKey / costMs
 * </pre>
 *
 * <h3>为什么不在这里写 wardrobe_item</h3>
 * 用户点「保存/放入衣橱」之前，数据库里<b>绝不能</b>出现这条衣物。
 * 因此本处理器只把「识别结果 + 产物 key」落到 {@code wardrobe_import_task}（{@code wardrobe_id} 保持 null
 * 即表示「尚未入库」）；真正的 INSERT 发生在用户确认后调用的
 * {@code POST /wardrobe/import/tasks/{taskId}/commit}（见 {@link WardrobeImportService#commit}）。
 * 这样「不点保存就不入库」的语义天然成立，不再需要在重识别/放弃时去删草稿条目。
 *
 * <h3>降级策略</h3>
 * <ol>
 *   <li>抠图未启用/失败 → bbox 矩形裁剪</li>
 *   <li>裁剪无 bbox/失败 → 直接用原图作封面</li>
 *   <li>模型调用失败 → 仍保留原图产物，任务置 FAILED，前端提示用户手动补全或重识别</li>
 * </ol>
 * 任一环节都不丢图，抠图失败更不例外。
 *
 * <h3>关于事务</h3>
 * 刻意不在整条流水线上加 {@code @Transactional}：中间包含 30s 级的外部 HTTP 调用（大模型）与外部
 * 调用（抠图、OSS 上传），长事务会长时间占用数据库连接，且外部副作用本就无法随事务回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WardrobeImportProcessor {

    private final WardrobeImportTaskMapper taskMapper;
    private final OssService ossService;
    private final GarmentAiProperties aiProperties;
    private final GarmentImageCropService cropService;
    private final ObjectProvider<GarmentRecognizeService> recognizeServiceProvider;
    /**
     * 抠图服务：{@code trybe.ai.matting.enabled != true} 时 Bean 缺失，{@code getIfAvailable()} 返回 null，
     * 处理器据此自动跳过抠图，退回本地裁剪 —— 未开通云端抠图服务时对现有流程零影响。
     */
    private final ObjectProvider<GarmentMattingService> mattingServiceProvider;

    /**
     * 处理导入任务
     *
     * @return true 表示任务已达终态（成功或业务性失败），消息可以 ACK；
     *         false 表示基础设施异常，建议重投重试
     */
    public boolean process(Long taskId) {
        WardrobeImportTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.error("[WardrobeImportProcessor] 任务不存在 taskId={}", taskId);
            return true;
        }

        ImportTaskStatus status = ImportTaskStatus.of(task.getStatus());
        if (status == ImportTaskStatus.SUCCESS || status == ImportTaskStatus.FAILED) {
            log.info("[WardrobeImportProcessor] 任务已终态，跳过 taskId={} status={}", taskId, status);
            return true;
        }

        long startMs = System.currentTimeMillis();
        markProcessing(task);

        try {
            // ========== 1. 取原图 ==========
            byte[] originBytes = readOrigin(task.getOriginObjectKey());
            if (originBytes == null || originBytes.length == 0) {
                log.error("[WardrobeImportProcessor] 原图读取失败 taskId={} key={}", taskId, task.getOriginObjectKey());
                return false;
            }

            // ========== 2. AI 识别 ==========
            GarmentRecognizeService recognizeService = recognizeServiceProvider.getIfAvailable();
            GarmentRecognizeResult result = null;
            String failReason = null;

            if (!Boolean.TRUE.equals(aiProperties.getEnabled())) {
                failReason = "AI识别功能已关闭，已保留原图";
                log.warn("[WardrobeImportProcessor] AI 未启用，走降级 taskId={}", taskId);
            } else if (recognizeService == null) {
                failReason = "未配置可用的识别模型";
                log.error("[WardrobeImportProcessor] 识别服务 Bean 缺失，走降级 taskId={}", taskId);
            } else {
                try {
                    byte[] forModel = cropService.compressForModel(originBytes,
                            aiProperties.getMaxImageBytes() == null ? 4194304 : aiProperties.getMaxImageBytes());
                    result = recognizeService.recognize(forModel, FileTypeUtils.contentTypeOfFile(task.getOriginObjectKey()));
                } catch (Exception e) {
                    failReason = StrUtil.maxLength("AI识别失败：" + e.getMessage(), 1000);
                    log.error("[WardrobeImportProcessor] AI 识别失败，走降级 taskId={}", taskId, e);
                }
            }

            // ========== 3. 抠图 → 裁剪 → 原图（三级降级） ==========
            String cutoutKey = tryMatting(task);
            if (cutoutKey == null) {
                cutoutKey = tryCropAndUpload(task, originBytes, result);
            }

            // ========== 4. 回写任务（不写 wardrobe_item！） ==========
            String usedModel = result == null ? null
                    : (recognizeService == null ? aiProperties.getModel() : recognizeService.modelName());
            long costMs = System.currentTimeMillis() - startMs;
            // wardrobe_id 刻意保持 null：表示「尚未入库」，waiting 用户点保存时的 commit 接口来写
            task.setWardrobeId(null);
            task.setCutoutObjectKey(cutoutKey);
            task.setAiModel(result == null ? aiProperties.getModel() : usedModel);
            task.setAiResultJson(result == null ? null : JSONUtil.toJsonStr(result));
            task.setAiRawJson(result == null ? null : result.getRawJson());
            task.setCostMs(costMs);
            task.setEndTime(LocalDateTime.now());
            task.setStatus(result == null ? ImportTaskStatus.FAILED.getCode() : ImportTaskStatus.SUCCESS.getCode());
            task.setFailReason(failReason);
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);

            log.info("[WardrobeImportProcessor] 处理完成 taskId={} aiSuccess={} cutoutKey={} costMs={} failReason={}",
                    taskId, result != null, cutoutKey, costMs, failReason);
            return true;

        } catch (Exception e) {
            log.error("[WardrobeImportProcessor] 处理出现基础设施异常，交由MQ重试 taskId={}", taskId, e);
            // 回退到 PENDING，让重投能被状态守卫接受
            task.setStatus(ImportTaskStatus.PENDING.getCode());
            task.setRetryCount((task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1);
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
            return false;
        }
    }

    // ==================== 私有方法 ====================

    private void markProcessing(WardrobeImportTask task) {
        task.setStatus(ImportTaskStatus.PROCESSING.getCode());
        task.setStartTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private byte[] readOrigin(String objectKey) {
        if (StrUtil.isBlank(objectKey)) {
            return null;
        }
        try (InputStream in = ossService.getObjectContent(objectKey)) {
            return IoUtil.readBytes(in);
        } catch (Exception e) {
            log.error("[WardrobeImportProcessor] 读取OSS原图失败 key={}", objectKey, e);
            return null;
        }
    }

    /**
     * 尝试像素级抠图（去背景 → 白底）
     *
     * <p>失败一律静默降级：抠图只是「锦上添花」，任何异常都不能让导入失败。
     *
     * @return 抠图结果的 objectKey；未启用/失败返回 null
     */
    private String tryMatting(WardrobeImportTask task) {
        GarmentMattingService mattingService = mattingServiceProvider.getIfAvailable();
        if (mattingService == null) {
            // 未启用抠图：不是错误，不打 warn，避免正常流程刷屏
            return null;
        }
        try {
            return mattingService.matting(task.getOriginObjectKey(), task.getUserId(), task.getTaskNo());
        } catch (Exception e) {
            log.warn("[WardrobeImportProcessor] 抠图失败，降级本地裁剪 taskId={} err={}", task.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * bbox 矩形裁剪并上传
     *
     * <p>裁剪未生效（无 bbox / 降级返回原图）时不重复存一份，直接返回 null 让封面回退原图。
     *
     * @return 裁剪图的 objectKey；未生效/失败返回 null
     */
    private String tryCropAndUpload(WardrobeImportTask task, byte[] originBytes, GarmentRecognizeResult result) {
        if (result == null) {
            return null;
        }
        try {
            byte[] cutoutBytes = cropService.cropToGarment(
                    originBytes, result.getBbox() == null ? null : result.getBbox());
            if (cutoutBytes == originBytes) {
                return null;
            }
            String cutoutKey = WardrobeOssPathBuilder.importCutout(task.getUserId(), task.getTaskNo());
            try (InputStream in = new ByteArrayInputStream(cutoutBytes)) {
                ossService.putObject(in, cutoutKey);
            }
            return cutoutKey;
        } catch (Exception e) {
            log.warn("[WardrobeImportProcessor] 裁剪/上传失败，降级使用原图 taskId={}", task.getId(), e);
            return null;
        }
    }
}
