package com.trybe.common.service;

import com.baidu.aip.bodyanalysis.AipBodyAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * <p>
 * 百度智能云手部关键点检测服务
 * </p>
 *
 * <p>调用百度人体分析 API 的手部关键点识别（hand_analysis）接口，
 * 检测图片中所有手部，识别每只手的 21 个主要骨节点。
 *
 * <p>API 接口：https://aip.baidubce.com/rest/2.0/image-classify/v1/hand_analysis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandKeypointDetectService {

    private final AipBodyAnalysis aipBodyAnalysis;

    /**
     * 对指定图片进行手部关键点检测
     *
     * <p>将图片流读取为字节数组后调用百度手部关键点识别 API（byte[] 重载），
     * 返回检测到的手部数量、每只手的 21 个关键点坐标及置信度等信息。
     *
     * @param imageStream 图片输入流（支持 jpg/png/bmp 格式，编码后 ≤ 4MB）
     * @return 手部关键点检测结果
     */
    public HandKeypointDetectResult detect(InputStream imageStream) {
        byte[] imageBytes;
        try {
            imageBytes = readAllBytes(imageStream);
        } catch (IOException e) {
            log.error("[HandKeypointDetect] 读取图片流失败", e);
            return HandKeypointDetectResult.builder()
                    .success(false)
                    .errorMessage("读取图片流失败: " + e.getMessage())
                    .build();
        }

        // 获取图片尺寸
        int imageWidth = 0;
        int imageHeight = 0;
        try (InputStream dimStream = new ByteArrayInputStream(imageBytes)) {
            BufferedImage image = ImageIO.read(dimStream);
            if (image != null) {
                imageWidth = image.getWidth();
                imageHeight = image.getHeight();
            }
        } catch (IOException e) {
            log.warn("[HandKeypointDetect] 无法获取图片尺寸", e);
        }

        HashMap<String, String> options = new HashMap<>();

        try {
            JSONObject response = aipBodyAnalysis.handAnalysis(imageBytes, options);
            return buildResult(response, imageWidth, imageHeight);
        } catch (Exception e) {
            log.error("[HandKeypointDetect] 手部关键点检测异常", e);
            return HandKeypointDetectResult.builder()
                    .success(false)
                    .errorMessage("手部关键点检测服务异常: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 解析百度 API 返回的 JSON，构建检测结果
     */
    private HandKeypointDetectResult buildResult(JSONObject response, int imageWidth, int imageHeight) {
        String logId = response.optString("log_id", "");

        // 检查错误码
        if (response.has("error_code")) {
            int errorCode = response.optInt("error_code");
            String errorMsg = response.optString("error_msg", "未知错误");
            log.error("[HandKeypointDetect] API返回错误 code={} msg={}", errorCode, errorMsg);
            return HandKeypointDetectResult.builder()
                    .success(false)
                    .errorCode(String.valueOf(errorCode))
                    .errorMessage(errorMsg)
                    .logId(logId)
                    .build();
        }

        int handNum = response.optInt("hand_num", 0);
        List<HandKeypointDetectResult.HandInfo> hands = new ArrayList<>();

        JSONArray handInfoArray = response.optJSONArray("hand_info");
        if (handInfoArray != null) {
            for (int i = 0; i < handInfoArray.length(); i++) {
                JSONObject handObj = handInfoArray.getJSONObject(i);
                HandKeypointDetectResult.HandInfo handInfo = parseHandInfo(handObj);
                hands.add(handInfo);
            }
        }

        return HandKeypointDetectResult.builder()
                .success(true)
                .handCount(handNum)
                .hands(hands)
                .imageWidth(imageWidth)
                .imageHeight(imageHeight)
                .logId(logId)
                .build();
    }

    /**
     * 解析单只手的信息
     */
    private HandKeypointDetectResult.HandInfo parseHandInfo(JSONObject handObj) {
        float score = (float) handObj.optDouble("score", 0.0);

        // 解析边界框
        JSONObject locationObj = handObj.optJSONObject("location");
        HandKeypointDetectResult.HandRectangle location = null;
        if (locationObj != null) {
            location = HandKeypointDetectResult.HandRectangle.builder()
                    .left(locationObj.optInt("left", 0))
                    .top(locationObj.optInt("top", 0))
                    .width(locationObj.optInt("width", 0))
                    .height(locationObj.optInt("height", 0))
                    .build();
        }

        // 解析 21 个关键点
        List<HandKeypointDetectResult.HandKeypoint> keypoints = new ArrayList<>();
        JSONObject handPartsObj = handObj.optJSONObject("hand_parts");
        if (handPartsObj != null) {
            for (int i = 0; i < 21; i++) {
                JSONObject keypointObj = handPartsObj.optJSONObject(String.valueOf(i));
                if (keypointObj != null) {
                    HandKeypointDetectResult.HandKeypoint keypoint = HandKeypointDetectResult.HandKeypoint.builder()
                            .x(keypointObj.optInt("x", 0))
                            .y(keypointObj.optInt("y", 0))
                            .score((float) keypointObj.optDouble("score", 0.0))
                            .build();
                    keypoints.add(keypoint);
                }
            }
        }

        return HandKeypointDetectResult.HandInfo.builder()
                .score(score)
                .location(location)
                .keypoints(keypoints)
                .build();
    }

    /**
     * 读取输入流全部字节
     */
    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int n;
        while ((n = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, n);
        }
        return buffer.toByteArray();
    }
}