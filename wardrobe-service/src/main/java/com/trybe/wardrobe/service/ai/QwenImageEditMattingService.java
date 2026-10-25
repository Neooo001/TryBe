package com.trybe.wardrobe.service.ai;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.trybe.common.ai.GarmentAiProperties;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BizIllegalException;
import com.trybe.common.service.OssService;
import com.trybe.common.utils.ImageUtils;
import com.trybe.wardrobe.config.GarmentMattingProperties;
import com.trybe.wardrobe.util.WardrobeOssPathBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * 抠图实现 —— 阿里云百炼「千问图像生成与编辑」（qwen-image-2.1）
 *
 * <h3>为什么不是「分割抠图」</h3>
 * 原方案用阿里云视觉智能开放平台的 {@code SegmentCloth}（服饰分割），但该产品已于
 * 2026-09-16 公告退市：<b>2026-10-11 起停止新用户开通与购买</b>，2027-04-11 停止老用户续费，
 * 2028-04-30 全面停止服务。新账号已无法调用，只能换方案。
 * 百炼是该公告点名的替代方案之一，而且本项目的衣服识别本来就在用百炼，<b>可复用同一把 api-key</b>。
 *
 * <h3>怎么拿到「白底」</h3>
 * <ol>
 *   <li>提示词里明确写「把背景换成纯白色（#FFFFFF）」——不写会保留原背景；</li>
 *   <li>{@code prompt_extend=false} —— 开着智能改写会把硬约束稀释掉；</li>
 *   <li>拿到结果后统一过 {@link WhiteBackdrop}：模型给白底就给白底，给透明就合成到白底，
 *       一律输出白底 JPEG 并把长边压到配置上限。</li>
 * </ol>
 *
 * <h3>降级与安全</h3>
 * <ul>
 *   <li>入参超过保守阈值（8MB / 4096px）先在本地压一份临时图再送云端，用完立即删除</li>
 *   <li>接口可能是同步返回，也可能只回 {@code task_id}（异步），两种都支持：拿到 task_id 就轮询</li>
 *   <li>全程不打印 api-key、预签名 URL、完整响应体，只打 objectKey / 耗时 / 状态 / 错误码</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnExpression("${trybe.ai.matting.enabled:false} and "
        + "'${trybe.ai.matting.provider:baidu}'.equals('qwen-image-edit')")
public class QwenImageEditMattingService implements GarmentMattingService {

    /** 预签名 URL 有效期（秒）：够云端拉图即可，不宜过长 */
    private static final long PRESIGN_TTL_SECONDS = 900L;
    /**
     * 送云端的入参体积上限。
     * <p>百炼对多模态入参未给出与分割抠图同样明确的 3MB 硬限，这里取 8MB 作保守阈值：
     * 超过先压缩，既避开未知上限，也能明显降低上传与推理耗时。
     */
    private static final int MAX_UPLOAD_BYTES = 8 * 1024 * 1024;
    /** 入参边长保守上限：与官方文生图/图生图 2048*2048 的像素面积口径对齐 */
    private static final int CLOUD_LIMIT_SIDE = 4096;
    /** 超限压缩的目标长边 */
    private static final int COMPRESS_TARGET_SIDE = 2048;
    /** 异步任务轮询间隔 */
    private static final long POLL_INTERVAL_MS = 1500L;
    /** 异步任务终态 */
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";

    private final GarmentMattingProperties properties;
    private final GarmentAiProperties garmentAiProperties;
    private final OssService ossService;

    @Override
    public String providerName() {
        return "bailian-qwen-image-edit";
    }

    @Override
    public String matting(String originObjectKey, Long userId, String taskNo, MattingHint hint) {
        String subject = hint == null ? null : hint.getSubject();
        if (StrUtil.isBlank(originObjectKey)) {
            throw new BizIllegalException(ErrorCodeEnum.PARAM_EMPTY, "原图为空，无法抠图");
        }

        PreparedInput input = null;
        try {
            // 1. 准备公网可访问的入参 URL（超限时先压缩并上传临时图）
            input = prepareInput(originObjectKey, userId, taskNo);

            // 2. 提交图像编辑任务并取回结果图 URL
            long startMs = System.currentTimeMillis();
            String resultUrl = requestMatting(input.url, buildPrompt(subject));
            long costMs = System.currentTimeMillis() - startMs;

            // 3. 下载结果图 → 统一合成为白底 JPEG（模型可能给白底也可能给透明，这里都兜住）
            byte[] raw = download(resultUrl);
            byte[] bytes = WhiteBackdrop.toWhiteJpeg(raw, whiteMaxSide());
            String objectKey = WardrobeOssPathBuilder.importMatting(userId, taskNo);
            try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
                ossService.putObject(in, objectKey);
            } catch (Exception e) {
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图结果上传失败");
            }

            log.info("[GarmentMatting] 抠图成功 provider={} subject={} costMs={} bytes={}(raw={}) objectKey={}",
                    providerName(), StrUtil.blankToDefault(subject, "-"), costMs, bytes.length, raw.length, objectKey);
            return objectKey;
        } finally {
            // 临时压缩图用完即删：无论成功失败都不能在 OSS 留孤儿文件
            if (input != null && input.tempKey != null) {
                deleteQuietly(input.tempKey);
            }
        }
    }

    // ==================== 提示词 ====================

    /**
     * 构造编辑指令
     *
     * <p>写法要点：<b>先说要什么、再逐项说不要什么、最后锁死"不得改变"</b>。
     * 生成式模型容易"顺手美化"，所以必须显式禁止改版型/改纹理/加细节，
     * 否则抠出来的衣服跟商品图不是一个东西。
     */
    private String buildPrompt(String subject) {
        String target = StrUtil.isBlank(subject)
                ? "图片中最主要的那件服装"
                : "图片中的" + subject.trim();
        return "只保留" + target + "，把背景换成纯白色（#FFFFFF，白色留白）。必须去掉：模特的身体、皮肤、头发、四肢，"
                + "模特身上的其他服饰（鞋、包、帽子、配饰），以及全部背景、地面、道具、文字和水印。"
                + "保留下来的衣服必须与原图完全一致：同样的款式、版型、颜色、图案、材质纹理、褶皱、"
                + "纽扣、拉链和 logo 位置，不得美化、不得新增或删减细节、不得改变颜色。"
                + "边缘要干净，不能有残影、锯齿或残留的背景色块。";
    }

    // ==================== 入参准备 ====================

    /**
     * 准备送云端的图片 URL
     *
     * <p>正常情况直接用原图预签名 URL（零额外 IO）；只有原图超限才在本地压一份临时图上传，
     * 换取「接口不因参数超限而失败」。
     */
    private PreparedInput prepareInput(String originObjectKey, Long userId, String taskNo) {
        byte[] originBytes = readOriginQuietly(originObjectKey);
        if (originBytes == null || originBytes.length == 0) {
            return new PreparedInput(presign(originObjectKey), null);
        }

        BufferedImage source = ImageUtils.read(originBytes);
        boolean oversized = originBytes.length > MAX_UPLOAD_BYTES
                || (source != null && Math.max(source.getWidth(), source.getHeight()) >= CLOUD_LIMIT_SIDE);
        if (!oversized) {
            return new PreparedInput(presign(originObjectKey), null);
        }

        byte[] compressed = compress(source);
        if (compressed == null || compressed.length == 0 || compressed.length > MAX_UPLOAD_BYTES) {
            // 压不下来（极少见）：仍送原图，交给云端报错 + 调用方降级，绝不因此中断导入
            log.warn("[GarmentMatting] 原图超限且压缩未达阈值，仍按原图调用 originBytes={}", originBytes.length);
            return new PreparedInput(presign(originObjectKey), null);
        }

        String tempKey = WardrobeOssPathBuilder.importMattingSource(userId, taskNo);
        try (ByteArrayInputStream in = new ByteArrayInputStream(compressed)) {
            ossService.putObject(in, tempKey);
        } catch (Exception e) {
            log.warn("[GarmentMatting] 临时压缩图上传失败，改送原图 err={}", e.getMessage());
            return new PreparedInput(presign(originObjectKey), null);
        }
        log.info("[GarmentMatting] 原图超限已压缩 originBytes={} compressedBytes={} tempKey={}",
                originBytes.length, compressed.length, tempKey);
        return new PreparedInput(presign(tempKey), tempKey);
    }

    /**
     * 压缩到保守阈值内
     *
     * @return 压缩后的 JPEG 字节；无法解码时返回 null
     */
    private byte[] compress(BufferedImage source) {
        if (source == null) {
            return null;
        }
        try {
            byte[] candidate = ImageUtils.writeJpeg(
                    ImageUtils.resizeToMaxSide(source, COMPRESS_TARGET_SIDE));
            if (candidate.length <= MAX_UPLOAD_BYTES) {
                return candidate;
            }
            // 仍超限：交给统一档位逐级降（1280 → 1024 → 800）
            return ImageUtils.compressForModel(candidate, MAX_UPLOAD_BYTES);
        } catch (Exception e) {
            log.warn("[GarmentMatting] 压缩异常 err={}", e.getMessage());
            return null;
        }
    }

    private byte[] readOriginQuietly(String objectKey) {
        try (InputStream in = ossService.getObjectContent(objectKey)) {
            return IoUtil.readBytes(in);
        } catch (Exception e) {
            log.warn("[GarmentMatting] 读取原图失败 key={} err={}", objectKey, e.getMessage());
            return null;
        }
    }

    private String presign(String objectKey) {
        String url = ossService.generatePresignedUrl(objectKey, PRESIGN_TTL_SECONDS);
        if (StrUtil.isBlank(url)) {
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "生成图片访问地址失败");
        }
        return url;
    }

    // ==================== 云端调用 ====================

    /**
     * 提交图像编辑任务，返回结果图 URL
     *
     * <p>该模型既可能直接同步返回结果，也可能只回 {@code task_id}，因此两种形态都要处理。
     */
    private String requestMatting(String imageUrl, String prompt) {
        JSONObject body = buildRequestBody(imageUrl, prompt);

        JSONObject root;
        try (HttpResponse response = HttpRequest.post(qwen().getBaseUrl())
                .header("Authorization", "Bearer " + apiKey())
                .header("Content-Type", "application/json")
                .header("X-DashScope-Async", "enable")
                .body(body.toString())
                .timeout(timeoutMs())
                .execute()) {
            String data = response.body();
            if (!response.isOk()) {
                log.warn("[GarmentMatting] 提交失败 status={} code={} message={}",
                        response.getStatus(), safeField(data, "code"), safeField(data, "message"));
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR,
                        "抠图服务调用失败(" + response.getStatus() + ")");
            }
            root = parseOrNull(data);
        } catch (BizIllegalException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[GarmentMatting] 提交异常 provider={} err={}", providerName(), e.getMessage());
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图服务异常");
        }

        if (root == null) {
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图返回体无法解析");
        }

        // 形态 1：同步返回，直接带图
        String direct = extractImageUrl(root);
        if (StrUtil.isNotBlank(direct)) {
            return direct;
        }

        // 形态 2：异步任务，轮询直到成功/超时
        JSONObject output = root.getJSONObject("output");
        String taskId = output == null ? null : output.getStr("task_id");
        if (StrUtil.isBlank(taskId)) {
            log.warn("[GarmentMatting] 返回体既无结果图也无 task_id requestId={}", root.getStr("request_id"));
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图结果为空");
        }
        return pollTask(taskId);
    }

    private JSONObject buildRequestBody(String imageUrl, String prompt) {
        JSONArray content = JSONUtil.createArray();
        content.add(JSONUtil.createObj().set("image", imageUrl));
        content.add(JSONUtil.createObj().set("text", prompt));

        JSONArray messages = JSONUtil.createArray();
        messages.add(JSONUtil.createObj().set("role", "user").set("content", content));

        JSONObject parameters = JSONUtil.createObj()
                .set("n", 1)
                // 关键：关掉提示词改写，否则"透明背景"约束会被模型补全掉
                .set("prompt_extend", Boolean.TRUE.equals(qwen().getPromptExtend()))
                .set("watermark", Boolean.TRUE.equals(qwen().getWatermark()));

        JSONObject input = JSONUtil.createObj().set("messages", messages);
        return JSONUtil.createObj()
                .set("model", qwen().getModel())
                .set("input", input)
                .set("parameters", parameters);
    }

    /**
     * 轮询异步任务直到成功、失败或总超时
     */
    private String pollTask(String taskId) {
        String url = StrUtil.removeSuffix(qwen().getTaskUrl(), "/") + "/" + taskId;
        long deadline = System.currentTimeMillis() + timeoutMs();
        String lastStatus = null;

        while (System.currentTimeMillis() < deadline) {
            sleepQuietly(POLL_INTERVAL_MS);
            JSONObject root;
            try (HttpResponse response = HttpRequest.get(url)
                    .header("Authorization", "Bearer " + apiKey())
                    .timeout(timeoutMs())
                    .execute()) {
                if (!response.isOk()) {
                    log.warn("[GarmentMatting] 轮询失败 status={}", response.getStatus());
                    continue;
                }
                root = parseOrNull(response.body());
            } catch (Exception e) {
                log.warn("[GarmentMatting] 轮询异常 err={}", e.getMessage());
                continue;
            }
            if (root == null) {
                continue;
            }
            JSONObject output = root.getJSONObject("output");
            if (output == null) {
                continue;
            }
            String status = output.getStr("task_status");
            lastStatus = status;
            if (STATUS_SUCCEEDED.equalsIgnoreCase(status)) {
                String result = extractImageUrl(root);
                if (StrUtil.isBlank(result)) {
                    throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图任务成功但无结果图");
                }
                return result;
            }
            if ("FAILED".equalsIgnoreCase(status) || "CANCELED".equalsIgnoreCase(status)
                    || "UNKNOWN".equalsIgnoreCase(status)) {
                log.warn("[GarmentMatting] 任务失败 status={} code={} message={}",
                        status, safeField(output.toString(), "code"), safeField(output.toString(), "message"));
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图任务失败(" + status + ")");
            }
        }
        log.warn("[GarmentMatting] 轮询超时 status={}", lastStatus);
        throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图任务超时");
    }

    /**
     * 从响应中提取结果图 URL
     *
     * <p>兼容两种结构：
     * <ul>
     *   <li>多模态同步返回：{@code output.choices[].message.content[].image}</li>
     *   <li>异步任务结果：{@code output.results[].url}</li>
     * </ul>
     */
    private String extractImageUrl(JSONObject root) {
        JSONObject output = root.getJSONObject("output");
        if (output == null) {
            return null;
        }
        JSONArray choices = output.getJSONArray("choices");
        if (choices != null) {
            for (int i = 0; i < choices.size(); i++) {
                JSONObject choice = choices.getJSONObject(i);
                JSONObject message = choice == null ? null : choice.getJSONObject("message");
                JSONArray content = message == null ? null : message.getJSONArray("content");
                if (content == null) {
                    continue;
                }
                for (int j = 0; j < content.size(); j++) {
                    JSONObject part = content.getJSONObject(j);
                    String image = part == null ? null : part.getStr("image");
                    if (StrUtil.isNotBlank(image)) {
                        return image;
                    }
                }
            }
        }
        JSONArray results = output.getJSONArray("results");
        if (results != null) {
            for (int i = 0; i < results.size(); i++) {
                JSONObject item = results.getJSONObject(i);
                if (item == null) {
                    continue;
                }
                String url = StrUtil.blankToDefault(item.getStr("url"), item.getStr("image_url"));
                if (StrUtil.isNotBlank(url)) {
                    return url;
                }
            }
        }
        return null;
    }

    private JSONObject parseOrNull(String body) {
        if (StrUtil.isBlank(body)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(body);
        } catch (Exception e) {
            log.warn("[GarmentMatting] 响应体解析失败");
            return null;
        }
    }

    /**
     * 只取错误码/错误文案，绝不整包打印响应体（可能回显提示词与图片 URL）
     */
    private String safeField(String body, String field) {
        JSONObject root = parseOrNull(body);
        return root == null ? null : root.getStr(field);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图任务被中断");
        }
    }

    private int timeoutMs() {
        return properties.getTimeoutMs() == null ? 60000 : properties.getTimeoutMs();
    }

    private int whiteMaxSide() {
        return properties.getWhiteMaxSide() == null
                ? WhiteBackdrop.DEFAULT_MAX_SIDE
                : properties.getWhiteMaxSide();
    }

    /**
     * 千问相关配置（与百度那套并列在 trybe.ai.matting 下，靠 provider 切换）
     */
    private GarmentMattingProperties.Qwen qwen() {
        return properties.getQwen();
    }

    /**
     * 解析 api-key：优先用抠图自己的配置，没配就复用衣服识别那把（同一个百炼账号）
     */
    private String apiKey() {
        String key = StrUtil.blankToDefault(qwen().getApiKey(), garmentAiProperties.getApiKey());
        if (StrUtil.isBlank(key)) {
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "未配置百炼 api-key");
        }
        return key;
    }

    /**
     * 下载结果图字节（带体积上限保护）
     */
    private byte[] download(String url) {
        int maxBytes = properties.getMaxBytes() == null ? 5242880 : properties.getMaxBytes();
        try (HttpResponse response = HttpRequest.get(url).timeout(timeoutMs()).execute()) {
            if (!response.isOk()) {
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR,
                        "抠图结果下载失败(" + response.getStatus() + ")");
            }
            byte[] bytes = response.bodyBytes();
            if (bytes == null || bytes.length == 0) {
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图结果为空");
            }
            if (bytes.length > maxBytes) {
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图结果体积超限");
            }
            return bytes;
        } catch (BizIllegalException e) {
            throw e;
        } catch (Exception e) {
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图结果下载异常");
        }
    }

    /**
     * 删除临时对象，失败只告警不抛出 —— 清理动作不能影响主流程返回结果
     */
    private void deleteQuietly(String objectKey) {
        try {
            ossService.deleteFile(objectKey);
        } catch (Exception e) {
            log.warn("[GarmentMatting] 临时文件清理失败 key={} err={}", objectKey, e.getMessage());
        }
    }

    /**
     * 入参准备结果：可访问 URL + 需要事后清理的临时对象 key（无临时对象时为 null）
     */
    private static final class PreparedInput {
        private final String url;
        private final String tempKey;

        private PreparedInput(String url, String tempKey) {
            this.url = url;
            this.tempKey = tempKey;
        }
    }
}
