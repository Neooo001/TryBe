package com.trybe.wardrobe.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BadRequestException;
import com.trybe.common.exception.ForbiddenException;
import com.trybe.common.service.OssService;
import com.trybe.common.utils.FileTypeUtils;
import com.trybe.wardrobe.config.GarmentAiProperties;
import com.trybe.wardrobe.domain.dto.GarmentRecognizeResult;
import com.trybe.wardrobe.domain.dto.UpdateWardrobeReq;
import com.trybe.wardrobe.domain.dto.WardrobeImportMessage;
import com.trybe.wardrobe.domain.enums.ImportTaskStatus;
import com.trybe.wardrobe.domain.enums.WardrobeSource;
import com.trybe.wardrobe.domain.po.WardrobeImportTask;
import com.trybe.wardrobe.domain.po.WardrobeItem;
import com.trybe.wardrobe.domain.vo.ImportTaskVO;
import com.trybe.wardrobe.domain.vo.WardrobeItemVO;
import com.trybe.wardrobe.mapper.WardrobeImportTaskMapper;
import com.trybe.wardrobe.mapper.WardrobeItemMapper;
import com.trybe.wardrobe.mq.WardrobeImportProducer;
import com.trybe.wardrobe.util.WardrobeOssPathBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;

/**
 * 衣服导入服务 —— 面向小程序的接口编排层
 *
 * <h3>交互时序</h3>
 * <pre>
 * 小程序                        wardrobe-service                 MQ / AI
 *  |-- POST /import/upload ------>|  原图存OSS + 建任务(PENDING)
 *  |<-- {taskId, status=0} -------|  投递消息 ------------------->|
 *  |                               |                              |-- 识别 + 抠图/裁剪
 *  |-- GET /tasks/{taskId} ------->|                              |-- 回写任务(SUCCESS/FAILED, 产物key)
 *  |<-- {status=2, item:{...}} ----|  item 由 ai_result_json 现场构造（未落库）
 *  |-- (用户编辑后) POST /tasks/{id}/commit -> INSERT wardrobe_item + 回写 task.wardrobeId
 * </pre>
 *
 * <p>刻意不做「上传接口内同步等待 AI」：大模型调用 5-30s 波动很大，
 * 同步会长时间占用小程序请求线程并极易超时；异步化后上传接口稳定在百毫秒级。
 *
 * <h3>关于事务</h3>
 * 本类绝多数方法都包含 OSS / MQ 这类「不受数据库事务保护」的外部交互，加事务只会带来两个坏处：
 * <ol>
 *   <li>OSS 上传耗时被计入事务时长，长事务长期占用数据库连接</li>
 *   <li>事务未提交就投递消息，消费者可能查不到任务记录，表现为「任务不存在」</li>
 * </ol>
 * 因此这些方法不加事务，失败通过任务状态表达（外部副作用本来就无法回滚）。
 * <b>唯一例外是 {@link #commit}</b>：它只做纯 DB 写（INSERT 条目 + UPDATE 任务），
 * 无任何外部交互，故加 {@code @Transactional} 让这两条写保持原子。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WardrobeImportService {

    /** 单张图片最大体积 20MB，与 spring.servlet.multipart.max-file-size 及小程序端 MAX_UPLOAD_SIZE 保持一致 */
    private static final long MAX_UPLOAD_BYTES = 20L * 1024 * 1024;

    private final WardrobeImportTaskMapper taskMapper;
    private final WardrobeItemMapper itemMapper;
    private final OssService ossService;
    private final WardrobeItemConverter converter;
    private final WardrobeImportProducer producer;
    private final WardrobeImportProcessor importProcessor;
    private final ThreadPoolTaskExecutor wardrobeImportExecutor;
    private final GarmentAiProperties aiProperties;

    // ==================== 上传 ====================

    /**
     * 上传衣服图片并创建异步识别任务
     *
     * @return 任务VO（status = PENDING），前端凭 taskId 轮询
     */
    public ImportTaskVO createTask(Long userId, MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BadRequestException(ErrorCodeEnum.PARAM_EMPTY, "请选择衣服图片");
        }
        if (image.getSize() > MAX_UPLOAD_BYTES) {
            throw new BadRequestException(ErrorCodeEnum.PARAM_LENGTH_LIMIT, "图片不能超过 20MB");
        }

        String extension = FileTypeUtils.extensionOf(image.getOriginalFilename());
        if (!FileTypeUtils.isSupportedImage(extension)) {
            throw new BadRequestException(ErrorCodeEnum.PARAM_FORMAT_ERR,
                    "仅支持 " + String.join("/", FileTypeUtils.supportedImageExtensions()) + " 格式图片");
        }

        String taskNo = IdUtil.getSnowflakeNextIdStr();
        String originKey = WardrobeOssPathBuilder.importOrigin(userId, taskNo, extension);
        try (ByteArrayInputStream in = new ByteArrayInputStream(image.getBytes())) {
            ossService.putObject(in, originKey);
        } catch (Exception e) {
            log.error("[WardrobeImport] 原图上传失败 userId={} taskNo={}", userId, taskNo, e);
            throw new BadRequestException(ErrorCodeEnum.THIRD_PARTY_ERROR, "图片上传失败，请稍后再试");
        }

        WardrobeImportTask task = new WardrobeImportTask();
        task.setTaskNo(taskNo);
        task.setUserId(userId);
        task.setSource(WardrobeSource.AI_IMPORT.getValue());
        task.setOriginObjectKey(originKey);
        task.setStatus(ImportTaskStatus.PENDING.getCode());
        task.setRetryCount(0);
        task.setAiModel(aiProperties.getModel());
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.insert(task);

        dispatch(task);

        log.info("[WardrobeImport] 任务创建成功 userId={} taskId={} taskNo={}",
                userId, task.getId(), taskNo);
        return toVO(task);
    }

    // ==================== 轮询查询 ====================

    /**
     * 查询任务进度与识别结果
     */
    public ImportTaskVO getTask(Long userId, Long taskId) {
        WardrobeImportTask task = requireOwnedTask(userId, taskId);
        return toVO(task);
    }

    // ==================== 确认入库（用户点「放入衣橱」） ====================

    /**
     * 确认入库：把识别结果 + 用户提交的表单落成正式衣柜条目
     *
     * <p>这是全流程中<b>唯一</b>会 INSERT {@code wardrobe_item} 的入口。
     * 用户不点保存就不调用本接口，数据库里也就绝不会出现这条衣物。
     *
     * <p>流程：鉴权 → 任务归属校验 → 幂等守卫 → 还原识别结果 → 构造条目 → 用户字段覆盖 → 落库 → 回写任务。
     * 幂等守卫针对「同一任务重复提交」：若 {@code task.wardrobeId} 已有值说明上一次已入库，
     * 直接返回既有条目，绝不重复插入（网络重试、用户连点都会命中这里）。
     */
    @Transactional
    public WardrobeItemVO commit(Long userId, Long taskId, UpdateWardrobeReq req) {
        WardrobeImportTask task = requireOwnedTask(userId, taskId);
        ImportTaskStatus status = ImportTaskStatus.of(task.getStatus());
        if (status == ImportTaskStatus.PENDING || status == ImportTaskStatus.PROCESSING) {
            throw new BadRequestException(ErrorCodeEnum.STATUS_ERROR, "识别尚未完成，请稍候再保存");
        }

        // 幂等守卫：已入库则直接返回既有条目，避免重复插入
        if (task.getWardrobeId() != null) {
            WardrobeItem existing = itemMapper.selectById(task.getWardrobeId());
            if (existing != null) {
                log.info("[WardrobeImport] 任务已入库，幂等返回 userId={} taskId={} wardrobeId={}",
                        userId, taskId, existing.getId());
                return converter.toVO(existing, null);
            }
            // 极端情况：wardrobeId 有值但条目已被用户删除，视为未入库，允许重新落库
            log.warn("[WardrobeImport] 任务 wardrobeId 指向的条目不存在，按未入库处理 taskId={} wardrobeId={}",
                    taskId, task.getWardrobeId());
        }

        // 还原识别结果（AI 失败/降级时为 null，此时构造「原图 + 未命名衣物 + 未分类」）
        GarmentRecognizeResult result = parseResult(task.getAiResultJson());
        WardrobeItem item = converter.buildImportItem(task.getUserId(), task.getOriginObjectKey(),
                task.getCutoutObjectKey(), result, task.getAiModel());
        // 用户提交的字段覆盖 AI 结果（非 null 才覆盖，避免误清空）
        if (req != null) {
            converter.applyUpdate(item, req);
        }
        itemMapper.insert(item);

        task.setWardrobeId(item.getId());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);

        log.info("[WardrobeImport] 确认入库成功 userId={} taskId={} wardrobeId={}",
                userId, taskId, item.getId());
        return converter.toVO(item, null);
    }

    // ==================== 重新识别 ====================

    /**
     * 重新识别：任务回到 PENDING 后重投消息
     *
     * <p>改造后任务未确认入库时本就没有草稿条目，故不再需要删条目；
     * 若任务<b>已入库</b>则拒绝重跑 —— 否则会把用户已确认的数据与新的识别结果割裂。
     */
    public ImportTaskVO retry(Long userId, Long taskId) {
        WardrobeImportTask task = requireOwnedTask(userId, taskId);
        if (task.getWardrobeId() != null) {
            throw new BadRequestException(ErrorCodeEnum.STATUS_ERROR,
                    "该导入已入库，如需重识别请先在衣橱中删除");
        }
        ImportTaskStatus status = ImportTaskStatus.of(task.getStatus());
        if (status == ImportTaskStatus.PENDING || status == ImportTaskStatus.PROCESSING) {
            throw new BadRequestException(ErrorCodeEnum.STATUS_ERROR, "任务正在处理中，请稍候");
        }

        // 清掉上一轮裁剪/抠图产物（新产物 key 可能不同，避免留孤儿文件）
        deleteFileQuietly(task.getCutoutObjectKey());

        task.setStatus(ImportTaskStatus.PENDING.getCode());
        task.setWardrobeId(null);
        task.setCutoutObjectKey(null);
        task.setAiResultJson(null);
        task.setAiRawJson(null);
        task.setFailReason(null);
        task.setCostMs(null);
        task.setStartTime(null);
        task.setEndTime(null);
        task.setRetryCount(0);
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);

        dispatch(task);

        log.info("[WardrobeImport] 任务重新识别 userId={} taskId={}", userId, taskId);
        return toVO(task);
    }

    // ==================== 放弃导入 ====================

    /**
     * 放弃导入：清理任务原图与裁剪产物，并逻辑删除任务
     *
     * <p>语义收敛为「放弃这次导入（清任务 + 清 OSS 产物）」。
     * 若任务<b>已入库</b>则拒绝 —— 此时原图/抠图 key 与条目共用同一份文件，
     * 直接删会误删用户已确认的数据，应引导用户去衣橱里删除条目。
     */
    public void discard(Long userId, Long taskId) {
        WardrobeImportTask task = requireOwnedTask(userId, taskId);
        if (task.getWardrobeId() != null) {
            throw new BadRequestException(ErrorCodeEnum.STATUS_ERROR,
                    "该导入已入库，如需删除请到衣橱中操作");
        }

        // 未 commit 的任务没有草稿条目，直接按任务自身记录的 key 清理 OSS，避免漏删
        deleteFileQuietly(task.getOriginObjectKey());
        deleteFileQuietly(task.getCutoutObjectKey());
        taskMapper.deleteById(task.getId());
        log.info("[WardrobeImport] 任务已放弃 userId={} taskId={}", userId, taskId);
    }

    // ==================== 私有方法 ====================

    /**
     * 派发识别任务：优先走 MQ，投递失败则本地降级
     *
     * <p>两条路径对前端完全等价 —— 都是「立即返回 taskId，稍后轮询拿结果」，
     * 区别只在识别流水线由哪个线程执行。这样即使 RabbitMQ 未启动、
     * 队列被误删或网络不通，「导入衣服」依然可用，不会成为毕设演示的单点。
     */
    private void dispatch(WardrobeImportTask task) {
        WardrobeImportMessage message = new WardrobeImportMessage();
        message.setTaskId(task.getId());
        message.setTaskNo(task.getTaskNo());
        message.setUserId(task.getUserId());
        message.setOriginObjectKey(task.getOriginObjectKey());
        message.setRetryCount(0);

        if (producer.sendImportTask(message)) {
            return;
        }
        log.warn("[WardrobeImport] MQ 投递失败，降级为本地异步处理 taskId={}", task.getId());
        fallbackToLocal(task.getId());
    }

    /**
     * 本地降级执行识别流水线
     *
     * <p>不阻塞调用线程：上传接口已返回，用户照常轮询；
     * 处理结果同样落库，对前端透明。
     */
    private void fallbackToLocal(Long taskId) {
        try {
            wardrobeImportExecutor.execute(() -> {
                try {
                    importProcessor.process(taskId);
                } catch (Exception e) {
                    log.error("[WardrobeImport] 本地降级处理异常 taskId={}", taskId, e);
                }
            });
        } catch (Exception e) {
            // 线程池提交也失败（极端情况）：任务留在 PENDING，用户可点「重新识别」
            log.error("[WardrobeImport] 本地降级提交失败，任务待补偿 taskId={}", taskId, e);
        }
    }

    private void deleteFileQuietly(String objectKey) {
        if (StrUtil.isBlank(objectKey)) {
            return;
        }
        try {
            ossService.deleteFile(objectKey);
        } catch (Exception e) {
            log.warn("[WardrobeImport] OSS文件删除失败，交由生命周期兜底 key={}", objectKey, e);
        }
    }

    private WardrobeImportTask requireOwnedTask(Long userId, Long taskId) {
        WardrobeImportTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BadRequestException(ErrorCodeEnum.DATA_NOT_FOUND, "导入任务不存在");
        }
        if (!task.getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCodeEnum.FORBIDDEN, "无权操作该任务");
        }
        return task;
    }

    /**
     * 还原任务里持久化的识别结果；为空或解析失败一律降级为 null（=「未识别」）
     */
    private GarmentRecognizeResult parseResult(String aiResultJson) {
        if (StrUtil.isBlank(aiResultJson)) {
            return null;
        }
        try {
            return JSONUtil.toBean(aiResultJson, GarmentRecognizeResult.class);
        } catch (Exception e) {
            log.warn("[WardrobeImport] ai_result_json 解析失败，降级为未识别");
            return null;
        }
    }

    /**
     * 由裁剪产物 key 推断抠图模式，供前端角标区分「已抠图 / AI裁剪 / 原图」
     */
    private String cutoutModeOf(String cutoutObjectKey) {
        if (StrUtil.isBlank(cutoutObjectKey)) {
            return ImportTaskVO.CUTOUT_MODE_ORIGIN;
        }
        return WardrobeOssPathBuilder.isMattingCutout(cutoutObjectKey)
                ? ImportTaskVO.CUTOUT_MODE_MATTING
                : ImportTaskVO.CUTOUT_MODE_CROP;
    }

    /**
     * 任务 → VO
     *
     * <p>未 commit 时（{@code wardrobeId == null}）任务处于 SUCCESS/FAILED，
     * 此时<b>现场由 ai_result_json 构造一个 WardrobeItemVO（不落库）</b> 给前端编辑器当初始值；
     * {@code wardrobeId} 保持 null。这样「识别结果可见」与「未入库」两件事不再矛盾。
     */
    private ImportTaskVO toVO(WardrobeImportTask task) {
        ImportTaskStatus status = ImportTaskStatus.of(task.getStatus());
        ImportTaskVO vo = new ImportTaskVO();
        vo.setTaskId(task.getId());
        vo.setTaskNo(task.getTaskNo());
        vo.setStatus(status.getCode());
        vo.setStatusText(status.getDesc());
        vo.setWardrobeId(task.getWardrobeId());
        vo.setFailReason(task.getFailReason());
        vo.setCostMs(task.getCostMs());
        vo.setOriginImageUrl(converter.presign(task.getOriginObjectKey()));
        vo.setCutoutImageUrl(converter.presign(task.getCutoutObjectKey()));
        vo.setCutoutMode(cutoutModeOf(task.getCutoutObjectKey()));

        if (task.getWardrobeId() != null) {
            // 已入库：直接读库里的正式条目
            WardrobeItem item = itemMapper.selectById(task.getWardrobeId());
            if (item != null) {
                vo.setItem(converter.toVO(item, null));
            }
        } else if (status == ImportTaskStatus.SUCCESS || status == ImportTaskStatus.FAILED) {
            // 未入库但已出结果：临时构造预览条目（不落库），wardrobeId 仍为 null
            GarmentRecognizeResult result = parseResult(task.getAiResultJson());
            WardrobeItem preview = converter.buildImportItem(task.getUserId(), task.getOriginObjectKey(),
                    task.getCutoutObjectKey(), result, task.getAiModel());
            vo.setItem(converter.toVO(preview, null));
        }
        return vo;
    }
}
