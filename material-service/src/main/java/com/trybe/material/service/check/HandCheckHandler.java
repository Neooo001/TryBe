package com.trybe.material.service.check;

import cn.hutool.json.JSONUtil;
import com.trybe.common.service.HandKeypointDetectResult;
import com.trybe.common.service.HandKeypointDetectService;
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
 * 手部素材校验处理器 — 百度智能云手部关键点检测
 * </p>
 *
 * <p>校验规则：
 * <ul>
 *   <li>文件格式检查：仅允许 jpg/jpeg/png/webp</li>
 *   <li>文件大小检查：不超过 10MB</li>
 *   <li>AI 手部关键点检测：调用百度智能云手部关键点识别 API
 *     <ul>
 *       <li>手掌数量校验：检测到的手掌数量 ≤ 2 只（非手指或关键点数量）</li>
 *       <li>手掌完整度校验：边界框不紧贴图片边缘（margin ≥ 2%）+ 21 个关键点全部检测到且置信度达标</li>
 *       <li>图片清晰度校验：手部整体置信度 ≥ 0.5 + 关键点平均置信度 ≥ 0.3</li>
 *     </ul>
 *   </li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HandCheckHandler implements MaterialCheckHandler {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;
    private static final String[] ALLOWED_EXTENSIONS = {"jpg", "jpeg", "png", "webp"};

    /**
     * 最大允许的手掌数量（手掌，非手指 / 关键点数量）
     */
    private static final int MAX_HAND_COUNT = 2;

    /**
     * 手部整体置信度阈值（0~1），低于此阈值表示检测结果不可靠
     */
    private static final float HAND_SCORE_THRESHOLD = 0.5F;

    /**
     * 单个关键点置信度阈值（0~1），低于此阈值表示该关键点定位不准确
     */
    private static final float KEYPOINT_SCORE_THRESHOLD = 0.3F;

    /**
     * 关键点平均置信度阈值（0~1），综合评估整只手的检测质量
     */
    private static final float KEYPOINT_AVG_SCORE_THRESHOLD = 0.5F;

    /**
     * 手部边界框与图片边缘的最小边距比例（相对于图片尺寸）
     * 若边界框某边距图片边缘小于此比例，判定手部被截断
     */
    private static final double HAND_MARGIN_RATIO = 0.02;

    /**
     * 21 个关键点的期望数量
     */
    private static final int EXPECTED_KEYPOINT_COUNT = 21;

    private final OssService ossService;
    private final HandKeypointDetectService handKeypointDetectService;

    @Override
    public CheckResult check(Material material) {
        log.info("[HandCheck] 开始手部素材校验 materialId={} fileName={}", material.getId(), material.getFileName());

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

        // 3. AI 手部关键点检测（百度智能云）
        boolean aiPassed = doAiHandDetect(material, details);
        if (!aiPassed) {
            return CheckResult.fail("手部检测未通过", JSONUtil.toJsonStr(details));
        }

        details.put("overall", "PASS");
        log.info("[HandCheck] 手部素材校验通过 materialId={}", material.getId());
        return CheckResult.pass("校验通过", JSONUtil.toJsonStr(details));
    }

    @Override
    public String supportedCheckType() {
        return "HAND_CHECK";
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
     * 真实 AI 手部关键点检测 — 调用百度智能云手部关键点识别 API
     *
     * <p>通过 OSS 获取图片流，提交给百度人体分析 API 进行手部关键点检测，
     * 同时校验手部数量、手掌完整度、图片清晰度。
     */
    private boolean doAiHandDetect(Material material, Map<String, Object> details) {
        InputStream imageStream = ossService.getObjectContent(material.getObjectKey());

        HandKeypointDetectResult result = handKeypointDetectService.detect(imageStream);

        Map<String, Object> aiResult = new LinkedHashMap<>();
        aiResult.put("logId", result.getLogId());

        if (!result.isSuccess()) {
            aiResult.put("error", result.getErrorMessage());
            aiResult.put("errorCode", result.getErrorCode());
            details.put("ai_hand_detect", aiResult);
            log.error("[HandCheck] AI手部关键点检测API调用失败 materialId={} error={}", material.getId(), result.getErrorMessage());
            return false;
        }

        int handCount = result.getHandCount();
        int imageWidth = result.getImageWidth();
        int imageHeight = result.getImageHeight();

        aiResult.put("handCount", handCount);
        aiResult.put("imageWidth", imageWidth);
        aiResult.put("imageHeight", imageHeight);

        // 1. 手掌数量校验：检测到的手掌数量 ≤ MAX_HAND_COUNT（非手指或关键点数量）
        if (handCount == 0) {
            aiResult.put("handCountCheck", "FAIL: 未检测到手部");
            details.put("ai_hand_detect", aiResult);
            log.warn("[HandCheck] 手部数量校验失败 materialId={} handCount={}", material.getId(), handCount);
            return false;
        }
        if (handCount > MAX_HAND_COUNT) {
            aiResult.put("handCountCheck", "FAIL: 检测到 " + handCount + " 只手，最多允许 " + MAX_HAND_COUNT + " 只");
            details.put("ai_hand_detect", aiResult);
            log.warn("[HandCheck] 手部数量校验失败 materialId={} handCount={}", material.getId(), handCount);
            return false;
        }
        aiResult.put("handCountCheck", "PASS: " + handCount + " 只手");

        // 2. 逐只手进行完整度与清晰度校验
        List<HandKeypointDetectResult.HandInfo> hands = result.getHands();
        for (int i = 0; i < hands.size(); i++) {
            HandKeypointDetectResult.HandInfo hand = hands.get(i);
            String handPrefix = "hand_" + i + "_";

            // 2.1 手部整体置信度校验（清晰度）
            if (!checkHandScore(hand, aiResult, handPrefix)) {
                details.put("ai_hand_detect", aiResult);
                log.warn("[HandCheck] 手部置信度校验失败 materialId={} handIndex={} score={}",
                        material.getId(), i, hand.getScore());
                return false;
            }
            log.info("[HandCheck] 手部置信度校验成功 materialId={} handIndex={} score={}",
                    material.getId(), i, hand.getScore());
            // 2.2 关键点完整度校验
            if (!checkKeypoints(hand, aiResult, handPrefix)) {
                details.put("ai_hand_detect", aiResult);
                log.warn("[HandCheck] 关键点校验失败 materialId={} handIndex={}",
                        material.getId(), i);
                return false;
            }
            log.info("[HandCheck] 关键点校验成功 materialId={} handIndex={}",
                    material.getId(), i);
            // 2.3 边界框完整性校验（手掌是否被截断）
            if (!checkHandBoundary(hand, imageWidth, imageHeight, aiResult, handPrefix)) {
                details.put("ai_hand_detect", aiResult);
                log.warn("[HandCheck] 手掌边界校验失败 materialId={} handIndex={}",
                        material.getId(), i);
                return false;
            }
            log.info("[HandCheck] 手掌边界校验成功 materialId={} handIndex={}",
                    material.getId(), i);
        }

        details.put("ai_hand_detect", aiResult);
        log.info("[HandCheck] AI手部关键点检测通过 materialId={} handCount={}", material.getId(), handCount);
        return true;
    }

    /**
     * 手部整体置信度校验
     *
     * <p>百度手部关键点 API 的 hand_info 层级不返回整体 score 字段，
     * 因此以所有关键点的平均置信度作为手部整体置信度，评估图片清晰度。
     */
    private boolean checkHandScore(HandKeypointDetectResult.HandInfo hand, Map<String, Object> aiResult, String prefix) {
        List<HandKeypointDetectResult.HandKeypoint> keypoints = hand.getKeypoints();
        float totalScore = 0F;
        for (HandKeypointDetectResult.HandKeypoint kp : keypoints) {
            totalScore += kp.getScore();
        }
        float score = keypoints.isEmpty() ? 0F : totalScore / keypoints.size();
        aiResult.put(prefix + "score", String.format("%.4f", score));

        if (score < HAND_SCORE_THRESHOLD) {
            aiResult.put(prefix + "scoreCheck", String.format(
                    "FAIL: 置信度 %.4f < %.2f（图片可能不清晰）", score, HAND_SCORE_THRESHOLD));
            return false;
        }
        aiResult.put(prefix + "scoreCheck", "PASS");
        return true;
    }

    /**
     * 关键点完整度校验
     *
     * <p>检查是否检测到完整的 21 个关键点，以及每个关键点的置信度是否达标。
     * 关键点缺失或置信度过低可能表示手部被遮挡或图片质量不佳。
     */
    private boolean checkKeypoints(HandKeypointDetectResult.HandInfo hand, Map<String, Object> aiResult, String prefix) {
        List<HandKeypointDetectResult.HandKeypoint> keypoints = hand.getKeypoints();
        int keypointCount = keypoints.size();
        aiResult.put(prefix + "keypointCount", keypointCount);

        // 关键点数量校验
        if (keypointCount < EXPECTED_KEYPOINT_COUNT) {
            aiResult.put(prefix + "keypointCheck", String.format(
                    "FAIL: 仅检测到 %d/%d 个关键点，手部可能不完整或被遮挡", keypointCount, EXPECTED_KEYPOINT_COUNT));
            return false;
        }

        // 逐关键点置信度校验 + 计算平均置信度
        float totalScore = 0F;
        int lowScoreCount = 0;
        for (int i = 0; i < keypoints.size(); i++) {
            HandKeypointDetectResult.HandKeypoint kp = keypoints.get(i);
            totalScore += kp.getScore();
            if (kp.getScore() < KEYPOINT_SCORE_THRESHOLD) {
                lowScoreCount++;
            }
        }
        float avgScore = totalScore / keypointCount;
        aiResult.put(prefix + "keypointAvgScore", String.format("%.4f", avgScore));
        aiResult.put(prefix + "keypointLowScoreCount", lowScoreCount);

        // 平均置信度校验
        if (avgScore < KEYPOINT_AVG_SCORE_THRESHOLD) {
            aiResult.put(prefix + "keypointCheck", String.format(
                    "FAIL: 关键点平均置信度 %.4f < %.2f（%d 个关键点置信度偏低）",
                    avgScore, KEYPOINT_AVG_SCORE_THRESHOLD, lowScoreCount));
            return false;
        }
        aiResult.put(prefix + "keypointCheck", "PASS");
        return true;
    }

    /**
     * 手掌边界完整性校验
     *
     * <p>检查手部边界框是否完全在图片内且有合理边距。
     * 若边界框紧贴图片边缘，判定手掌被截断，不满足"手掌完整、无截断"的要求。
     */
    private boolean checkHandBoundary(HandKeypointDetectResult.HandInfo hand,
                                      int imageWidth, int imageHeight,
                                      Map<String, Object> aiResult, String prefix) {
        HandKeypointDetectResult.HandRectangle location = hand.getLocation();
        if (location == null) {
            aiResult.put(prefix + "boundaryCheck", "SKIP: 未返回手部边界框");
            return true;
        }

        int minMarginX = (int) (imageWidth * HAND_MARGIN_RATIO);
        int minMarginY = (int) (imageHeight * HAND_MARGIN_RATIO);

        boolean leftOk = location.getLeft() >= minMarginX;
        boolean topOk = location.getTop() >= minMarginY;
        boolean rightOk = (location.getLeft() + location.getWidth()) <= (imageWidth - minMarginX);
        boolean bottomOk = (location.getTop() + location.getHeight()) <= (imageHeight - minMarginY);

        aiResult.put(prefix + "boundary", String.format(
                "rect=[%d,%d,%d,%d] marginX=%d marginY=%d image=%dx%d",
                location.getLeft(), location.getTop(),
                location.getWidth(), location.getHeight(),
                minMarginX, minMarginY, imageWidth, imageHeight));

        if (!leftOk || !topOk || !rightOk || !bottomOk) {
            StringBuilder edgeIssue = new StringBuilder("手部边界框超出边界: ");
            if (!leftOk) edgeIssue.append("左侧越界 ");
            if (!topOk) edgeIssue.append("顶部越界 ");
            if (!rightOk) edgeIssue.append("右侧越界 ");
            if (!bottomOk) edgeIssue.append("底部越界 ");
            aiResult.put(prefix + "boundaryCheck", "FAIL: " + edgeIssue.toString().trim());
            return false;
        }
        aiResult.put(prefix + "boundaryCheck", "PASS");
        return true;
    }
}