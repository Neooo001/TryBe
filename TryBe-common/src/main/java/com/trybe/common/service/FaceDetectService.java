package com.trybe.common.service;

import com.aliyun.facebody20191230.Client;
import com.aliyun.facebody20191230.models.DetectFaceAdvanceRequest;
import com.aliyun.facebody20191230.models.DetectFaceRequest;
import com.aliyun.facebody20191230.models.DetectFaceResponse;
import com.aliyun.facebody20191230.models.DetectFaceResponseBody;
import com.aliyun.facebody20191230.models.DetectFaceResponseBody.DetectFaceResponseBodyData;
import com.aliyun.facebody20191230.models.DetectFaceResponseBody.DetectFaceResponseBodyDataQualities;
import com.aliyun.tea.TeaException;
import com.aliyun.teautil.models.RuntimeOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 阿里云人脸检测服务
 *
 * <p>调用阿里云视觉智能开放平台的人脸检测与五官定位 (DetectFace) API，
 * 对图片进行人脸检测，返回人脸数量、置信度、矩形框坐标及图片尺寸等信息。
 * <p>
 * 开启 Landmark 关键点检测，从 105 个关键点中提取 9 个外轮廓点（索引 96-104）
 * 计算完整头部矩形框，用于更精确的人脸占比校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaceDetectService {

    private final Client facebodyClient;

    /**
     * 设置图片中人脸的最大返回数量，设为 2 以便检测是否存在多张人脸
     */
    private static final Long MAX_FACE_NUMBER = 2L;

    /**
     * 对指定图片进行人脸检测（通过 InputStream 上传，不受 OSS 地域限制）
     *
     * @param imageStream 图片输入流
     * @return 人脸检测结果
     */
    public FaceDetectResult detect(InputStream imageStream) {
        byte[] imageBytes;
        try {
            imageBytes = readAllBytes(imageStream);
        } catch (IOException e) {
            log.error("[FaceDetect] 读取图片流失败", e);
            return FaceDetectResult.builder()
                    .success(false)
                    .errorMessage("读取图片流失败: " + e.getMessage())
                    .build();
        }

        int imageWidth = 0;
        int imageHeight = 0;
        try (InputStream dimStream = new ByteArrayInputStream(imageBytes)) {
            BufferedImage image = ImageIO.read(dimStream);
            if (image != null) {
                imageWidth = image.getWidth();
                imageHeight = image.getHeight();
            }
        } catch (IOException e) {
            log.warn("[FaceDetect] 无法获取图片尺寸", e);
        }

        DetectFaceAdvanceRequest request = new DetectFaceAdvanceRequest()
                .setImageURLObject(new ByteArrayInputStream(imageBytes))
                .setMaxFaceNumber(MAX_FACE_NUMBER)
                .setLandmark(true);

        try {
            DetectFaceResponse response = facebodyClient.detectFaceAdvance(request, new RuntimeOptions());
            FaceDetectResult result = buildResult(response);
            result.setImageWidth(imageWidth);
            result.setImageHeight(imageHeight);
            return result;
        } catch (TeaException e) {
            log.error("[FaceDetect] 人脸检测API调用失败 code={} message={}", e.getCode(), e.getMessage(), e);
            return FaceDetectResult.builder()
                    .success(false)
                    .errorCode(e.getCode())
                    .errorMessage(e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("[FaceDetect] 人脸检测异常", e);
            return FaceDetectResult.builder()
                    .success(false)
                    .errorMessage("人脸检测服务异常: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 对指定图片进行人脸检测（通过 URL，要求图片与API同地域）
     *
     * @param imageUrl 图片的 OSS 预签名 URL（需与 facebody 同地域）
     * @return 人脸检测结果
     */
    public FaceDetectResult detect(String imageUrl) {
        DetectFaceRequest request = new DetectFaceRequest()
                .setImageURL(imageUrl)
                .setMaxFaceNumber(MAX_FACE_NUMBER)
                .setLandmark(true);

        try {
            DetectFaceResponse response = facebodyClient.detectFace(request);
            return buildResult(response);
        } catch (TeaException e) {
            log.error("[FaceDetect] 阿里云人脸检测API调用失败 code={} message={}", e.getCode(), e.getMessage(), e);
            return FaceDetectResult.builder()
                    .success(false)
                    .errorCode(e.getCode())
                    .errorMessage(e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("[FaceDetect] 人脸检测异常", e);
            return FaceDetectResult.builder()
                    .success(false)
                    .errorMessage("人脸检测服务异常: " + e.getMessage())
                    .build();
        }
    }

    private FaceDetectResult buildResult(DetectFaceResponse response) {
        DetectFaceResponseBody body = response.getBody();
        DetectFaceResponseBodyData data = body.getData();

        List<Float> faceProbabilityList = data.getFaceProbabilityList() != null
                ? data.getFaceProbabilityList()
                : Collections.emptyList();
        Integer faceCount = data.getFaceCount();

        List<FaceDetectResult.FaceRectangle> faceRectangles = parseFaceRectangles(data.getFaceRectangles());

        // 解析质量分
        Float fnfScore = null;
        Float qualityScore = null;
        Float blurScore = null;
        DetectFaceResponseBodyDataQualities qualities = data.getQualities();
        if (qualities != null) {
            fnfScore = firstElement(qualities.getFnfList());
            qualityScore = firstElement(qualities.getScoreList());
            blurScore = firstElement(qualities.getBlurList());
        }

        // 解析关键点 + 计算头部矩形框
        FaceDetectResult.FaceRectangle headRectangle = computeHeadRectangle(data.getLandmarks(), data.getLandmarkCount());

        return FaceDetectResult.builder()
                .success(true)
                .faceCount(faceCount != null ? faceCount : 0)
                .faceProbabilities(faceProbabilityList)
                .faceRectangles(faceRectangles)
                .headRectangle(headRectangle)
                .fnfScore(fnfScore)
                .qualityScore(qualityScore)
                .blurScore(blurScore)
                .poseList(data.getPoseList())
                .landmarkCount(data.getLandmarkCount())
                .requestId(body.getRequestId())
                .build();
    }

    /**
     * 获取列表的第一个元素，列表为空或 null 时返回 null
     */
    private Float firstElement(List<Float> list) {
        if (list != null && !list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }

    /**
     * 从 105 个关键点中提取 9 个外轮廓点（索引 96-104），计算头部矩形框
     *
     * <p>104 个关键点分布：眉毛24点(0-23)、眼睛32点(24-55)、鼻子6点(56-61)、
     * 嘴巴34点(62-95)、外轮廓9点(96-104)
     * <p>
     * 外轮廓 9 个点围成的区域即为完整的头部区域，可用于更精确的人脸占比和完整性校验
     *
     * @param landmarks     关键点坐标列表 [x0, y0, x1, y1, ...]
     * @param landmarkCount 关键点数量（固定 105）
     * @return 头部矩形框，若关键点数据不足则返回 null
     */
    private FaceDetectResult.FaceRectangle computeHeadRectangle(List<Float> landmarks, Integer landmarkCount) {
        if (landmarks == null || landmarks.isEmpty() || landmarkCount == null || landmarkCount < 105) {
            return null;
        }

        // 105 个关键点 = 210 个坐标值，外轮廓 9 个点 = 索引 96-104，即坐标索引 192-209
        int contourStart = 96 * 2;   // 192
        int contourEnd = 105 * 2;    // 210
        if (landmarks.size() < contourEnd) {
            return null;
        }

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;

        for (int i = contourStart; i < contourEnd; i += 2) {
            float x = landmarks.get(i);
            float y = landmarks.get(i + 1);
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
        }

        return FaceDetectResult.FaceRectangle.builder()
                .left((int) minX)
                .top((int) minY)
                .width((int) (maxX - minX))
                .height((int) (maxY - minY))
                .build();
    }

    /**
     * 解析 API 返回的人脸矩形框坐标
     * API 返回格式：[left1, top1, width1, height1, left2, top2, width2, height2, ...]
     */
    private List<FaceDetectResult.FaceRectangle> parseFaceRectangles(List<Integer> rawRectangles) {
        if (rawRectangles == null || rawRectangles.isEmpty() || rawRectangles.size() < 4) {
            return Collections.emptyList();
        }

        List<FaceDetectResult.FaceRectangle> rectangles = new ArrayList<>();
        int faceNum = rawRectangles.size() / 4;
        for (int i = 0; i < faceNum; i++) {
            int offset = i * 4;
            rectangles.add(FaceDetectResult.FaceRectangle.builder()
                    .left(rawRectangles.get(offset))
                    .top(rawRectangles.get(offset + 1))
                    .width(rawRectangles.get(offset + 2))
                    .height(rawRectangles.get(offset + 3))
                    .build());
        }
        return rectangles;
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int n;
        while ((n = inputStream.read(data)) != -1) {
            buffer.write(data, 0, n);
        }
        return buffer.toByteArray();
    }
}