package com.trybe.common.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 百度手部关键点检测结果 DTO
 * </p>
 *
 * <p>封装百度手部关键点识别 API 的返回结果，包含手部数量、每只手的21个关键点信息、
 * 边界框坐标以及整体置信度。
 *
 * <p>21 个关键点索引说明：
 * <ul>
 *   <li>0：手腕</li>
 *   <li>1-4：拇指（从根部到指尖）</li>
 *   <li>5-8：食指（从根部到指尖）</li>
 *   <li>9-12：中指（从根部到指尖）</li>
 *   <li>13-16：无名指（从根部到指尖）</li>
 *   <li>17-20：小指（从根部到指尖）</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandKeypointDetectResult {

    /** API 调用是否成功 */
    private boolean success;

    /** 检测到的手部数量 */
    private int handCount;

    /** 每只手的信息列表 */
    @Builder.Default
    private List<HandInfo> hands = new ArrayList<>();

    /** 图片宽度（像素） */
    private int imageWidth;

    /** 图片高度（像素） */
    private int imageHeight;

    /** 百度 API 请求唯一标识 */
    private String logId;

    /** 错误码 */
    private String errorCode;

    /** 错误信息 */
    private String errorMessage;

    /**
     * 单只手的信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HandInfo {

        /** 手部整体置信度分数（0~1），分数越高表示检测结果越可靠 */
        private float score;

        /** 手部边界框 */
        private HandRectangle location;

        /** 21 个关键点列表，索引 0~20 */
        @Builder.Default
        private List<HandKeypoint> keypoints = new ArrayList<>();
    }

    /**
     * 手部边界框
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HandRectangle {

        /** 边界框左上角 X 坐标 */
        private int left;

        /** 边界框左上角 Y 坐标 */
        private int top;

        /** 边界框宽度 */
        private int width;

        /** 边界框高度 */
        private int height;

        /**
         * 计算边界框面积（像素²）
         */
        public long area() {
            return (long) width * height;
        }
    }

    /**
     * 单个关键点
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HandKeypoint {

        /** 关键点在图片中的 X 坐标 */
        private int x;

        /** 关键点在图片中的 Y 坐标 */
        private int y;

        /** 关键点置信度分数（0~1），分数越高表示该关键点定位越准确 */
        private float score;
    }
}