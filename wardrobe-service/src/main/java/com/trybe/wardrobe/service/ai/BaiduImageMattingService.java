package com.trybe.wardrobe.service.ai;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.trybe.common.ai.GarmentRecognizeResult;
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
 * 抠图实现 —— 百度智能云「智能抠图」（image-process/v1/segment）
 *
 * <h3>它只有两种模式（这是本实现的关键）</h3>
 * <ul>
 *   <li>{@code method=auto}：自动找画面主体。<b>画面主体是整个人</b>，所以模特穿着衣服的图会被
 *       连同腿、鞋、包一起抠出来 —— 这也是「导入长裤却带上了鞋」的原因</li>
 *   <li>{@code method=control}：<b>只在你给的矩形框里找主体</b>。把 AI 识别出的衣服 bbox 传进去，
 *       框外的腿、脚、鞋、背景就不会被算作主体</li>
 * </ul>
 * 没有「按品类分割」能力（那是已退市的阿里云 SegmentCloth 才有的），所以这里用 control 模式
 * 逼近「只抠衣服」的效果 —— 零额外成本，只是把已有的 bbox 用起来。
 *
 * <h3>接口约束（踩过就报错，必须先处理）</h3>
 * <ul>
 *   <li>入参 base64 后 ≤10MB，<b>最短边 ≥128px、最长边 ≤3000px</b> → 超限先缩放/压缩</li>
 *   <li>base64 不能带 {@code data:image/...} 前缀</li>
 *   <li>{@code method} 必填；{@code return_form=rgba} 才返回透明四通道 PNG</li>
 *   <li>{@code position} 格式为 {@code [[[x1,y1],[x2,y2]]]}，<b>框不能贴图片边缘</b>、尺寸要 &gt;10×10</li>
 *   <li>鉴权走 {@code access_token}（约 30 天有效），进程内缓存 + 过期前 5 分钟续期</li>
 * </ul>
 *
 * <p><b>安全</b>：不打印 API Key / Secret / access_token，也不打印图片 base64；只打 log_id、耗时、体积。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnExpression("${trybe.ai.matting.enabled:false} and "
        + "'${trybe.ai.matting.provider:baidu}'.equals('baidu')")
public class BaiduImageMattingService implements GarmentMattingService {

    /** 入参 base64 后的体积上限（官方 10MB），留一点余量 */
    private static final int MAX_BASE64_LENGTH = 10 * 1024 * 1024 - 65536;
    /** 入参最长边上限（官方要求 ≤3000px） */
    private static final int MAX_SIDE = 3000;
    /** 入参最短边下限（官方要求 ≥128px） */
    private static final int MIN_SIDE = 128;
    /** 框与图片边缘的最小间距（官方要求框不能贴边） */
    private static final int BOX_EDGE_MARGIN = 2;
    /** 框的最小边长（官方要求 >10×10） */
    private static final int BOX_MIN_SIZE = 12;
    /** token 提前续期的安全余量（毫秒） */
    private static final long TOKEN_REFRESH_AHEAD_MS = 5 * 60 * 1000L;
    /** 手动框选模式 */
    private static final String METHOD_CONTROL = "control";
    /** 自动识别主体模式 */
    private static final String METHOD_AUTO = "auto";

    private final GarmentMattingProperties properties;
    private final OssService ossService;

    /** access_token 进程内缓存 */
    private volatile String accessToken;
    private volatile long tokenExpireAtMs;

    @Override
    public String providerName() {
        return "baidu-image-segment";
    }

    @Override
    public String matting(String originObjectKey, Long userId, String taskNo, MattingHint hint) {
        if (StrUtil.isBlank(originObjectKey)) {
            throw new BizIllegalException(ErrorCodeEnum.PARAM_EMPTY, "原图为空，无法抠图");
        }

        byte[] originBytes = readOriginQuietly(originObjectKey);
        if (originBytes == null || originBytes.length == 0) {
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "读取原图失败");
        }

        // 1. 预处理成符合官方约束的 base64（同时拿到实际送云的像素尺寸，用于换算框坐标）
        PreparedInput prepared = prepareInput(originBytes);

        // 2. 有可用 bbox 就走「手动框选主体」，把范围收紧到衣服，避免把腿/鞋/包一起抠出来
        JSONArray position = buildPosition(hint, prepared.width, prepared.height);
        String method = position != null ? METHOD_CONTROL : METHOD_AUTO;

        // 3. 调用抠图
        long startMs = System.currentTimeMillis();
        byte[] png = callSegment(prepared.base64, method, position);
        long costMs = System.currentTimeMillis() - startMs;

        // 4. 透明前景 → 白底 JPEG（服务端只给 rgba，没有白底参数，只能自己合成）
        byte[] result = WhiteBackdrop.toWhiteJpeg(png, whiteMaxSide());

        // 5. 上传到抠图路径（必须走 PathBuilder，禁止手拼）
        String objectKey = WardrobeOssPathBuilder.importMatting(userId, taskNo);
        try (ByteArrayInputStream in = new ByteArrayInputStream(result)) {
            ossService.putObject(in, objectKey);
        } catch (Exception e) {
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图结果上传失败");
        }

        log.info("[GarmentMatting] 抠图成功 provider={} method={} costMs={} bytes={}(raw={}) objectKey={}",
                providerName(), method, costMs, result.length, png.length, objectKey);
        return objectKey;
    }

    // ==================== 主体框 → position ====================

    /**
     * 把归一化 bbox 换算成百度要求的像素框
     *
     * <p>格式：{@code [[[x1,y1],[x2,y2]]]}（外层是框数组，内层是左上/右下两点）。
     * 官方硬约束：<b>框不能与图片边缘重合</b>、尺寸要大于 10×10，所以这里做内缩与最小尺寸修正；
     * 任何一步修正后不合法就返回 null（退回 auto 模式），绝不传一个非法框让接口报错。
     *
     * @param hint   抠图提示，可为 null
     * @param width  实际送云图片的宽（像素）
     * @param height 实际送云图片的高（像素）
     */
    private JSONArray buildPosition(MattingHint hint, int width, int height) {
        if (hint == null || !hint.hasValidBbox()) {
            return null;
        }
        // 尺寸未知（走体积压缩分支时会置 0）或过小：算不出可信的框，直接退回 auto。
        // 不能交给 clamp 兜底 —— 边界倒挂会被夹成角落里的一个假小框，反而把主体切掉。
        if (width < BOX_EDGE_MARGIN * 2 + BOX_MIN_SIZE || height < BOX_EDGE_MARGIN * 2 + BOX_MIN_SIZE) {
            log.info("[GarmentMatting] 送云图片尺寸不可用({}x{})，跳过框选走 auto", width, height);
            return null;
        }
        GarmentRecognizeResult.Bbox bbox = hint.getBbox();

        int x1 = clamp((int) Math.floor(bbox.getX1() * width), BOX_EDGE_MARGIN, width - BOX_EDGE_MARGIN - BOX_MIN_SIZE);
        int y1 = clamp((int) Math.floor(bbox.getY1() * height), BOX_EDGE_MARGIN, height - BOX_EDGE_MARGIN - BOX_MIN_SIZE);
        int x2 = clamp((int) Math.ceil(bbox.getX2() * width), x1 + BOX_MIN_SIZE, width - BOX_EDGE_MARGIN);
        int y2 = clamp((int) Math.ceil(bbox.getY2() * height), y1 + BOX_MIN_SIZE, height - BOX_EDGE_MARGIN);

        if (x2 - x1 < BOX_MIN_SIZE || y2 - y1 < BOX_MIN_SIZE) {
            return null;
        }

        JSONArray box = JSONUtil.createArray();
        box.add(JSONUtil.createArray().set(x1).set(y1));
        box.add(JSONUtil.createArray().set(x2).set(y2));
        JSONArray boxes = JSONUtil.createArray();
        boxes.add(box);

        log.info("[GarmentMatting] 使用主体框收紧范围 image={}x{} box=({},{})({},{})", width, height, x1, y1, x2, y2);
        return boxes;
    }

    private int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(value, max));
    }

    // ==================== 入参预处理 ====================

    /**
     * 把原图转成符合百度约束的 base64，并带上实际像素尺寸
     *
     * <p>约束：base64 ≤10MB、最短边 ≥128、最长边 ≤3000。原图多半是手机拍摄/电商图，
     * 长边常超 3000，必须先缩放；极小的图（缩略图）再放大到最短边 128。
     */
    private PreparedInput prepareInput(byte[] originBytes) {
        byte[] prepared = originBytes;
        int width = 0;
        int height = 0;

        BufferedImage source = ImageUtils.read(originBytes);
        if (source != null) {
            width = source.getWidth();
            height = source.getHeight();
            int maxSide = Math.max(width, height);
            int minSide = Math.min(width, height);

            double scale = 1.0;
            if (maxSide > MAX_SIDE) {
                scale = (double) MAX_SIDE / maxSide;
            }
            if (minSide * scale < MIN_SIDE) {
                scale = (double) MIN_SIDE / minSide;
            }
            if (Math.abs(scale - 1.0) > 0.001) {
                int targetW = Math.max(1, (int) Math.round(width * scale));
                int targetH = Math.max(1, (int) Math.round(height * scale));
                try {
                    prepared = ImageUtils.writeJpeg(ImageUtils.resize(source, targetW, targetH));
                    log.info("[GarmentMatting] 入参尺寸调整 {}x{} -> {}x{}", width, height, targetW, targetH);
                    width = targetW;
                    height = targetH;
                } catch (Exception e) {
                    log.warn("[GarmentMatting] 入参缩放失败，按原图提交 err={}", e.getMessage());
                    prepared = originBytes;
                }
            }
        }

        String base64 = Base64.encode(prepared);
        if (base64.length() > MAX_BASE64_LENGTH) {
            byte[] compressed = ImageUtils.compressForModel(prepared, 6 * 1024 * 1024);
            base64 = Base64.encode(compressed);
            if (base64.length() > MAX_BASE64_LENGTH) {
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "原图过大，无法压缩到接口限制内");
            }
            log.info("[GarmentMatting] 入参体积压缩 {} -> {} bytes", prepared.length, compressed.length);
            // 尺寸可能因压缩而变化，框坐标是按老尺寸算的，这里保守放弃框选
            width = 0;
            height = 0;
        }
        return new PreparedInput(base64, width, height);
    }

    private byte[] readOriginQuietly(String objectKey) {
        try (InputStream in = ossService.getObjectContent(objectKey)) {
            return IoUtil.readBytes(in);
        } catch (Exception e) {
            log.warn("[GarmentMatting] 读取原图失败 key={} err={}", objectKey, e.getMessage());
            return null;
        }
    }

    // ==================== 云端调用 ====================

    /**
     * 调用智能抠图，返回透明 PNG 字节
     *
     * @param method   控制模式（control 需带 position）
     * @param position 主体框，可为 null
     */
    private byte[] callSegment(String imageBase64, String method, JSONArray position) {
        GarmentMattingProperties.Baidu cfg = properties.getBaidu();
        JSONObject body = JSONUtil.createObj()
                .set("image", imageBase64)
                .set("method", method)
                // 只有 rgba 才返回透明四通道 PNG
                .set("return_form", StrUtil.blankToDefault(cfg.getReturnForm(), "rgba"))
                .set("refine_mask", String.valueOf(Boolean.TRUE.equals(cfg.getRefineMask())));
        if (position != null) {
            body.set("position", position);
        }

        String url = StrUtil.removeSuffix(cfg.getBaseUrl(), "/") + "?access_token=" + accessToken();

        try (HttpResponse response = HttpRequest.post(url)
                .header("Content-Type", "application/json; charset=UTF-8")
                .body(body.toString())
                .timeout(timeoutMs())
                .execute()) {

            String data = response.body();
            if (!response.isOk()) {
                log.warn("[GarmentMatting] 百度接口 HTTP {} logId={}", response.getStatus(), safeField(data, "log_id"));
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR,
                        "抠图服务调用失败(" + response.getStatus() + ")");
            }

            JSONObject root = parseOrNull(data);
            if (root == null) {
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图返回体无法解析");
            }
            // 业务错误走 200 + error_code
            Integer errorCode = root.getInt("error_code");
            if (errorCode != null && errorCode != 0) {
                log.warn("[GarmentMatting] 百度接口业务错误 errorCode={} errorMsg={} logId={}",
                        errorCode, root.getStr("error_msg"), root.getStr("log_id"));
                // 110/111 = access_token 失效，清缓存让下次重取
                if (errorCode == 110 || errorCode == 111) {
                    accessToken = null;
                }
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图失败(" + errorCode + ")");
            }

            String resultBase64 = root.getStr("image");
            if (StrUtil.isBlank(resultBase64)) {
                log.warn("[GarmentMatting] 返回体无 image 字段 logId={}", root.getStr("log_id"));
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图结果为空");
            }
            byte[] png = Base64.decode(resultBase64);
            if (png == null || png.length == 0) {
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图结果解码失败");
            }
            int maxBytes = properties.getMaxBytes() == null ? 5242880 : properties.getMaxBytes();
            if (png.length > maxBytes) {
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图结果体积超限");
            }
            return png;
        } catch (BizIllegalException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[GarmentMatting] 调用异常 provider={} err={}", providerName(), e.getMessage());
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图服务异常");
        }
    }

    /**
     * 取 access_token（进程内缓存，过期前 5 分钟自动续）
     */
    private String accessToken() {
        String cached = accessToken;
        if (cached != null && System.currentTimeMillis() < tokenExpireAtMs - TOKEN_REFRESH_AHEAD_MS) {
            return cached;
        }
        synchronized (this) {
            if (accessToken != null && System.currentTimeMillis() < tokenExpireAtMs - TOKEN_REFRESH_AHEAD_MS) {
                return accessToken;
            }
            GarmentMattingProperties.Baidu cfg = properties.getBaidu();
            if (StrUtil.isBlank(cfg.getApiKey()) || StrUtil.isBlank(cfg.getSecretKey())) {
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "未配置百度抠图 API Key / Secret Key");
            }
            String url = StrUtil.removeSuffix(cfg.getTokenUrl(), "/")
                    + "?grant_type=client_credentials"
                    + "&client_id=" + cfg.getApiKey()
                    + "&client_secret=" + cfg.getSecretKey();
            try (HttpResponse response = HttpRequest.post(url).timeout(timeoutMs()).execute()) {
                String data = response.body();
                JSONObject root = parseOrNull(data);
                String token = root == null ? null : root.getStr("access_token");
                if (!response.isOk() || StrUtil.isBlank(token)) {
                    // 只暴露错误码，不打印响应体（里面可能回显 client_id）
                    log.warn("[GarmentMatting] 获取 access_token 失败 status={} error={}",
                            response.getStatus(), safeField(data, "error"));
                    throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "获取百度访问令牌失败");
                }
                long expiresInMs = root.getLong("expires_in", 2592000L) * 1000L;
                accessToken = token;
                tokenExpireAtMs = System.currentTimeMillis() + expiresInMs;
                log.info("[GarmentMatting] 百度 access_token 已刷新，有效期约 {} 天", expiresInMs / 86400000L);
                return token;
            } catch (BizIllegalException e) {
                throw e;
            } catch (Exception e) {
                log.warn("[GarmentMatting] 获取 access_token 异常 err={}", e.getMessage());
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "获取百度访问令牌异常");
            }
        }
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
     * 只取指定字段做日志，避免把 base64 / 密钥回显打出来
     */
    private String safeField(String body, String field) {
        JSONObject root = parseOrNull(body);
        return root == null ? null : root.getStr(field);
    }

    private int timeoutMs() {
        return properties.getTimeoutMs() == null ? 30000 : properties.getTimeoutMs();
    }

    private int whiteMaxSide() {
        return properties.getWhiteMaxSide() == null
                ? WhiteBackdrop.DEFAULT_MAX_SIDE
                : properties.getWhiteMaxSide();
    }

    /**
     * 入参准备结果：base64 + 实际像素尺寸（尺寸用于把归一化 bbox 换成像素框）
     */
    private static final class PreparedInput {
        private final String base64;
        private final int width;
        private final int height;

        private PreparedInput(String base64, int width, int height) {
            this.base64 = base64;
            this.width = width;
            this.height = height;
        }
    }
}
