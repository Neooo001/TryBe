package com.trybe.material.service.check;

import cn.hutool.json.JSONUtil;
import com.trybe.common.service.FaceDetectResult;
import com.trybe.common.service.FaceDetectService;
import com.trybe.common.service.OssService;
import com.trybe.material.domain.po.Material;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 人脸素材校验处理器 — 阿里云 DetectFace API（人脸检测与五官定位）
 * </p>
 *
 * <p>校验规则：
 * <ul>
 *   <li>文件格式检查：仅允许 jpg/jpeg/png/webp</li>
 *   <li>文件大小检查：不超过 10MB</li>
 *   <li>AI 人脸检测：调用阿里云视觉智能平台 DetectFace API
 *     <ul>
 *       <li>人脸数量校验：图片中只允许有 1 张人脸</li>
 *       <li>人脸置信度校验：置信度 ≥ 0.5</li>
 *       <li>人脸占比校验：基于 105 关键点外轮廓计算头部区域，面积 ≥ 图片面积的 50%</li>
 *       <li>人脸完整度校验：综合使用阿里云 API 返回的 fnfScore（完整度）、
 *           qualityScore（综合质量）、blurScore（模糊度）、头部边界等多项指标</li>
 *     </ul>
 *   </li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaceCheckHandler implements MaterialCheckHandler {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L; // 10MB
    private static final String[] ALLOWED_EXTENSIONS = {"jpg", "jpeg", "png", "webp"};

    private static final float FACE_CONFIDENCE_THRESHOLD = 0.5F;

    /**
     * 头部占比阈值：头部区域必须 ≥ 图片面积的 10%
     */
    private static final double HEAD_RATIO_THRESHOLD = 0.1;

    /**
     * 降级方案：当外轮廓关键点不可用时，用扩展系数将面部特征框扩展为头部区域
     */
    private static final double FALLBACK_WIDTH_EXPAND = 1.4;
    private static final double FALLBACK_HEIGHT_EXPAND = 1.8;

    /**
     * 人脸完整度阈值（来自阿里云 Qualities.fnfList），取值范围 (0, 100]
     * 半张脸等不完整人脸分数显著偏低，推荐 ≥ 80
     */
    private static final float FNF_SCORE_THRESHOLD = 80F;

    /**
     * 综合质量分阈值（来自阿里云 Qualities.scoreList），取值范围 (0, 100]
     * 推荐 ≥ 85
     */
    private static final float QUALITY_SCORE_THRESHOLD = 85F;

    /**
     * 模糊度阈值（来自阿里云 Qualities.blurList），取值范围 (0, 100]
     * 分数越高表示越模糊，推荐 ≤ 20
     */
    private static final float BLUR_SCORE_THRESHOLD = 20F;

    /**
     * 头部矩形框边界最小边距比例（相对于图片尺寸）
     * 若头部矩形框某边距图片边缘小于此比例，判定人脸不完整（被裁剪）
     */
    private static final double HEAD_MARGIN_RATIO = 0.02;

    private final OssService ossService;
    private final FaceDetectService faceDetectService;

    @Override
    public CheckResult check(Material material) {
        log.info("[FaceCheck] 开始人脸素材校验 materialId={} fileName={}", material.getId(), material.getFileName());

        Map<String, Object> details = new LinkedHashMap<>();

        // 1. 文件格式校验
        boolean formatPassed = checkFormat(material, details);
        if (!formatPassed) {
            return CheckResult.fail("文件格式不支持", JSONUtil.toJsonStr(details));
        }

        // 2. 文件大小校验
        boolean sizePassed = checkSize(material, details);
        if (!sizePassed) {
            return CheckResult.fail("文件大小超出限制", JSONUtil.toJsonStr(details));
        }

        // 3. AI 人脸检测（阿里云DetectFace API）
        boolean aiPassed = doAiFaceDetect(material, details);
        if (!aiPassed) {
            return CheckResult.fail("人脸检测未通过", JSONUtil.toJsonStr(details));
        }

        details.put("overall", "PASS");
        log.info("[FaceCheck] 人脸素材校验通过 materialId={}", material.getId());
        return CheckResult.pass("校验通过", JSONUtil.toJsonStr(details));
    }

    @Override
    public String supportedCheckType() {
        return "FACE_CHECK";
    }

    // ==================== 子校验方法 ====================

    private boolean checkFormat(Material material, Map<String, Object> details) {
        String fileName = material.getFileName();
        if (fileName == null) {
            details.put("format", "FAIL: 文件名为空");
            return false;
        }
        String ext = "";
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx >= 0) {
            ext = fileName.substring(dotIdx + 1).toLowerCase();
        }
        for (String allowed : ALLOWED_EXTENSIONS) {
            if (allowed.equals(ext)) {
                details.put("format", "PASS: " + ext);
                return true;
            }
        }
        details.put("format", "FAIL: 不支持扩展名 " + ext + ", 允许: " + String.join(",", ALLOWED_EXTENSIONS));
        return false;
    }

    private boolean checkSize(Material material, Map<String, Object> details) {
        Long fileSize = material.getFileSize();
        if (fileSize == null || fileSize <= 0) {
            details.put("size", "FAIL: 文件大小为0");
            return false;
        }
        if (fileSize > MAX_FILE_SIZE) {
            details.put("size", String.format("FAIL: %d bytes 超过上限 %d bytes", fileSize, MAX_FILE_SIZE));
            return false;
        }
        details.put("size", "PASS: " + fileSize + " bytes");
        return true;
    }

    /**
     * 真实 AI 人脸检测 — 调用阿里云 DetectFace API
     *
     * <p>通过 OSS 将图片提交给阿里云视觉智能平台进行人脸检测，同时校验：
     * <ul>
     *   <li>图片中只允许有一张人脸</li>
     *   <li>人脸置信度 ≥ 0.5</li>
     *   <li>人脸占比 ≥ 图片面积的 50%：
     *     优先使用 105 关键点外轮廓计算头部区域，外轮廓不可用时降级为面部特征框扩展法</li>
     * </ul>
     */
    private boolean doAiFaceDetect(Material material, Map<String, Object> details) {
        InputStream imageStream = ossService.getObjectContent(material.getObjectKey());

        FaceDetectResult result = faceDetectService.detect(imageStream);

        Map<String, Object> aiResult = new LinkedHashMap<>();
        aiResult.put("requestId", result.getRequestId());

        if (!result.isSuccess()) {
            aiResult.put("error", result.getErrorMessage());
            aiResult.put("errorCode", result.getErrorCode());
            details.put("ai_face_detect", aiResult);
            log.error("[FaceCheck] AI人脸检测API调用失败 materialId={} error={}", material.getId(), result.getErrorMessage());
            return false;
        }

        int faceCount = result.getFaceCount();
        List<Float> probabilities = result.getFaceProbabilities();
        List<FaceDetectResult.FaceRectangle> faceRectangles = result.getFaceRectangles();
        FaceDetectResult.FaceRectangle headRectangle = result.getHeadRectangle();
        int imageWidth = result.getImageWidth();
        int imageHeight = result.getImageHeight();

        aiResult.put("faceCount", faceCount);
        aiResult.put("faceProbabilities", probabilities);
        aiResult.put("imageWidth", imageWidth);
        aiResult.put("imageHeight", imageHeight);

        // 1. 人脸数量校验：只允许有一张人脸
        if (faceCount != 1) {
            aiResult.put("faceCountCheck", "FAIL: 检测到 " + faceCount + " 张人脸，只允许 1 张人脸");
            details.put("ai_face_detect", aiResult);
            log.warn("[FaceCheck] 人脸数量校验失败 materialId={} faceCount={}", material.getId(), faceCount);
            return false;
        }
        aiResult.put("faceCountCheck", "PASS: 1 张人脸");

        // 2. 人脸置信度校验
        boolean confidencePassed = false;
        if (probabilities != null && !probabilities.isEmpty()) {
            Float prob = probabilities.get(0);
            if (prob != null && prob >= FACE_CONFIDENCE_THRESHOLD) {
                confidencePassed = true;
            }
        }
        aiResult.put("confidencePassed", confidencePassed);
        aiResult.put("confidenceThreshold", FACE_CONFIDENCE_THRESHOLD);

        if (!confidencePassed) {
            aiResult.put("confidenceCheck", "FAIL: 人脸置信度不满足阈值 " + FACE_CONFIDENCE_THRESHOLD);
            details.put("ai_face_detect", aiResult);
            log.warn("[FaceCheck] 人脸置信度校验失败 materialId={}", material.getId());
            return false;
        }
        aiResult.put("confidenceCheck", "PASS");

        // 3. 人脸占比校验：头部区域 ≥ 图片面积的 50%
        // 优先使用 105 关键点外轮廓计算的头部矩形框，降级方案为面部特征框扩展法
        if (imageWidth <= 0 || imageHeight <= 0) {
            aiResult.put("faceRatioCheck", "SKIP: 无法获取图片尺寸");
            details.put("ai_face_detect", aiResult);
            log.warn("[FaceCheck] 无法获取图片尺寸 materialId={}", material.getId());
            return false;
        }

        long imageArea = (long) imageWidth * imageHeight;
        aiResult.put("imageArea", imageArea);

        if (headRectangle != null) {
            // 方案A：外轮廓关键点法（精度最高）
            long headArea = headRectangle.area();
            double headRatio = (double) headArea / imageArea;

            aiResult.put("headRect", String.format("left=%d, top=%d, width=%d, height=%d (外轮廓关键点)",
                    headRectangle.getLeft(), headRectangle.getTop(),
                    headRectangle.getWidth(), headRectangle.getHeight()));
            aiResult.put("headRectMethod", "contour");
            aiResult.put("headArea", headArea);
            aiResult.put("headRatio", String.format("%.2f%%", headRatio * 100));

            if (headRatio < HEAD_RATIO_THRESHOLD) {
                aiResult.put("faceRatioCheck", String.format(
                        "FAIL: 头部占比 %.2f%% 不满足最小 %d%% 要求（外轮廓法）",
                        headRatio * 100, (int) (HEAD_RATIO_THRESHOLD * 100)));
                details.put("ai_face_detect", aiResult);
                log.warn("[FaceCheck] 人脸占比校验失败 materialId={} headRatio={}% (contour)",
                        material.getId(), String.format("%.2f", headRatio * 100));
                return false;
            }
            aiResult.put("faceRatioCheck", "PASS: " + String.format("%.2f%% (外轮廓法)", headRatio * 100));
        } else if (faceRectangles != null && !faceRectangles.isEmpty()) {
            // 方案B：降级 — 面部特征框扩展法
            FaceDetectResult.FaceRectangle faceRect = faceRectangles.get(0);
            double rawFaceRatio = (double) faceRect.area() / imageArea;
            aiResult.put("faceRect", String.format("left=%d, top=%d, width=%d, height=%d (API面部特征框)",
                    faceRect.getLeft(), faceRect.getTop(), faceRect.getWidth(), faceRect.getHeight()));
            aiResult.put("faceRectRatio", String.format("%.2f%%", rawFaceRatio * 100));

            int expandedWidth = (int) (faceRect.getWidth() * FALLBACK_WIDTH_EXPAND);
            int expandedHeight = (int) (faceRect.getHeight() * FALLBACK_HEIGHT_EXPAND);
            long expandedArea = (long) expandedWidth * expandedHeight;
            double expandedRatio = (double) expandedArea / imageArea;

            aiResult.put("headRectMethod", "fallback_expand");
            aiResult.put("headRect", String.format("left=%d, top=%d, width=%d, height=%d (扩展法, wx%.1f hx%.1f)",
                    faceRect.getLeft(), faceRect.getTop(), expandedWidth, expandedHeight,
                    FALLBACK_WIDTH_EXPAND, FALLBACK_HEIGHT_EXPAND));
            aiResult.put("headArea", expandedArea);
            aiResult.put("headRatio", String.format("%.2f%%", expandedRatio * 100));

            if (expandedRatio < HEAD_RATIO_THRESHOLD) {
                aiResult.put("faceRatioCheck", String.format(
                        "FAIL: 头部占比 %.2f%% 不满足最小 %d%% 要求（扩展法）",
                        expandedRatio * 100, (int) (HEAD_RATIO_THRESHOLD * 100)));
                details.put("ai_face_detect", aiResult);
                log.warn("[FaceCheck] 人脸占比校验失败 materialId={} headRatio={}% (fallback)",
                        material.getId(), String.format("%.2f", expandedRatio * 100));
                return false;
            }
            aiResult.put("faceRatioCheck", "PASS: " + String.format("%.2f%% (扩展法)", expandedRatio * 100));
        } else {
            aiResult.put("faceRatioCheck", "SKIP: 无法获取人脸矩形框");
            log.warn("[FaceCheck] 无法获取人脸矩形框 materialId={}", material.getId());
        }

        // 4. 人脸完整度校验：通过阿里云官方质量分判断人脸是否完整清晰
        boolean completenessPassed = checkFaceCompleteness(result, aiResult);
        if (!completenessPassed) {
            details.put("ai_face_detect", aiResult);
            log.warn("[FaceCheck] 人脸完整度校验失败 materialId={}", material.getId());
            return false;
        }

        details.put("ai_face_detect", aiResult);
        log.info("[FaceCheck] AI人脸检测通过 materialId={} faceCount={} headRatio={}",
                material.getId(), faceCount,
                aiResult.getOrDefault("headRatio", "N/A"));
        return true;
    }

    /**
     * 人脸完整度校验
     *
     * <p>综合使用阿里云 API 返回的多个质量维度判断人脸是否完整清晰：
     * <ul>
     *   <li><b>人脸完整度（fnfScore）</b>：来自 Qualities.fnfList，直接反映人脸是否完整，
     *       半张脸等不完整人脸此分数显著偏低，阈值 ≥ 80</li>
     *   <li><b>综合质量分（qualityScore）</b>：来自 Qualities.scoreList，
     *       综合模糊、遮挡、光照、完整度等多维度，阈值 ≥ 85</li>
     *   <li><b>模糊度（blurScore）</b>：来自 Qualities.blurList，分数越高越模糊，阈值 ≤ 20</li>
     *   <li><b>头部边界校验</b>：检查头部矩形框是否完全在图片内且有合理边距，
     *       若头部框紧贴图片边缘则判定人脸被裁剪</li>
     * </ul>
     *
     * @return true 表示人脸完整清晰，false 表示不满足要求
     */
    private boolean checkFaceCompleteness(FaceDetectResult result, Map<String, Object> aiResult) {
        boolean allPassed = true;
        StringBuilder failReasons = new StringBuilder();

        // 4.1 人脸完整度校验（fnfScore）
        Float fnfScore = result.getFnfScore();
        aiResult.put("fnfScore", fnfScore != null ? String.format("%.2f", fnfScore) : "N/A");
        if (fnfScore != null && fnfScore < FNF_SCORE_THRESHOLD) {
            allPassed = false;
            failReasons.append(String.format("人脸完整度 %.2f < %.0f; ", fnfScore, FNF_SCORE_THRESHOLD));
        } else {
            aiResult.put("fnfCheck", fnfScore != null ? "PASS" : "SKIP: 未返回完整度分数");
        }

        // 4.2 综合质量分校验（qualityScore）
        Float qualityScore = result.getQualityScore();
        aiResult.put("qualityScore", qualityScore != null ? String.format("%.2f", qualityScore) : "N/A");
        if (qualityScore != null && qualityScore < QUALITY_SCORE_THRESHOLD) {
            allPassed = false;
            failReasons.append(String.format("综合质量分 %.2f < %.0f; ", qualityScore, QUALITY_SCORE_THRESHOLD));
        } else {
            aiResult.put("qualityCheck", qualityScore != null ? "PASS" : "SKIP: 未返回综合质量分");
        }

        // 4.3 模糊度校验（blurScore）
        Float blurScore = result.getBlurScore();
        aiResult.put("blurScore", blurScore != null ? String.format("%.2f", blurScore) : "N/A");
        if (blurScore != null && blurScore > BLUR_SCORE_THRESHOLD) {
            allPassed = false;
            failReasons.append(String.format("模糊度 %.2f > %.0f; ", blurScore, BLUR_SCORE_THRESHOLD));
        } else {
            aiResult.put("blurCheck", blurScore != null ? "PASS" : "SKIP: 未返回模糊度分数");
        }

        // 4.4 头部边界校验：头部矩形框不能紧贴图片边缘
        FaceDetectResult.FaceRectangle headRect = result.getHeadRectangle();
        int imageWidth = result.getImageWidth();
        int imageHeight = result.getImageHeight();
        if (headRect != null && imageWidth > 0 && imageHeight > 0) {
            int minMarginX = (int) (imageWidth * HEAD_MARGIN_RATIO);
            int minMarginY = (int) (imageHeight * HEAD_MARGIN_RATIO);

            boolean leftOk = headRect.getLeft() >= minMarginX;
            boolean topOk = headRect.getTop() >= minMarginY;
            boolean rightOk = (headRect.getLeft() + headRect.getWidth()) <= (imageWidth - minMarginX);
            boolean bottomOk = (headRect.getTop() + headRect.getHeight()) <= (imageHeight - minMarginY);

            aiResult.put("headBoundary", String.format(
                    "left=%d(top=%d,right=%d,bottom=%d) marginX=%d marginY=%d image=%dx%d",
                    headRect.getLeft(), headRect.getTop(),
                    headRect.getLeft() + headRect.getWidth(),
                    headRect.getTop() + headRect.getHeight(),
                    minMarginX, minMarginY, imageWidth, imageHeight));

            if (!leftOk || !topOk || !rightOk || !bottomOk) {
                allPassed = false;
                StringBuilder edgeIssue = new StringBuilder("头部矩形框超出边界: ");
                if (!leftOk) edgeIssue.append("左侧越界 ");
                if (!topOk) edgeIssue.append("顶部越界 ");
                if (!rightOk) edgeIssue.append("右侧越界 ");
                if (!bottomOk) edgeIssue.append("底部越界 ");
                edgeIssue.append("; ");
                failReasons.append(edgeIssue);
            } else {
                aiResult.put("headBoundaryCheck", "PASS");
            }
        } else {
            aiResult.put("headBoundaryCheck", "SKIP: 无法获取头部矩形框或图片尺寸");
        }

        if (!allPassed) {
            aiResult.put("completenessCheck", "FAIL: " + failReasons.toString().trim());
        } else {
            aiResult.put("completenessCheck", "PASS");
        }

        return allPassed;
    }
}