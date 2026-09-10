package com.trybe.common.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 人脸检测结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaceDetectResult {

    /** API 调用是否成功 */
    private boolean success;

    /** 检测到的人脸数量 */
    private int faceCount;

    /** 人脸置信度列表（0~1），与检测到的人脸一一对应 */
    private List<Float> faceProbabilities;

    /** 人脸矩形框列表，每个元素包含 [left, top, width, height]（API 面部特征紧框） */
    @Builder.Default
    private List<FaceRectangle> faceRectangles = new ArrayList<>();

    /**
     * 头部矩形框（基于 105 关键点中外轮廓点计算，精度高于面部特征框）
     * 若 API 未返回 LandmarkList 则为 null
     */
    private FaceRectangle headRectangle;

    /**
     * 人脸完整度分数（来自 Qualities.fnfList），取值范围 (0, 100]
     * 分数越高表示人脸越完整，半张脸等不完整人脸分数显著偏低
     */
    private Float fnfScore;

    /**
     * 综合质量分（来自 Qualities.scoreList），取值范围 (0, 100]
     * 综合了模糊、遮挡、光照、完整度等多个维度，推荐阈值 ≥ 85
     */
    private Float qualityScore;

    /**
     * 模糊度分数（来自 Qualities.blurList），取值范围 (0, 100]
     * 分数越高表示越模糊
     */
    private Float blurScore;

    /**
     * 人脸姿态角列表 [yaw, pitch, roll]
     * yaw: 左右角度 (-90~90)，pitch: 上下角度 (-90~90)，roll: 平面旋转 (-180~180)
     */
    private List<Float> poseList;

    /**
     * 关键点数量，固定为 105
     */
    private Integer landmarkCount;

    /** 图片宽度（像素） */
    private int imageWidth;

    /** 图片高度（像素） */
    private int imageHeight;

    /** 阿里云请求ID */
    private String requestId;

    /** 错误码 */
    private String errorCode;

    /** 错误信息 */
    private String errorMessage;

    /**
     * 单个人脸矩形框
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FaceRectangle {
        /** 人脸框左上角 X 坐标 */
        private int left;
        /** 人脸框左上角 Y 坐标 */
        private int top;
        /** 人脸框宽度 */
        private int width;
        /** 人脸框高度 */
        private int height;

        /**
         * 计算人脸框面积（像素²）
         */
        public long area() {
            return (long) width * height;
        }
    }

    /**
     * 计算图片总面积（像素²）
     */
    public long imageArea() {
        return (long) imageWidth * imageHeight;
    }
}