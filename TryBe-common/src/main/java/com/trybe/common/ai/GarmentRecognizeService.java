package com.trybe.common.ai;

/**
 * 衣服识别服务（供应商可替换）
 *
 * <p>当前唯一实现为 {@link QwenVlGarmentRecognizeService}（阿里云百炼 Qwen-VL）。
 * 抽象成接口的目的：换供应商时只需新增一个 {@code @ConditionalOnProperty} 实现类，
 * 调用方（衣橱导入、创作者商品发布）一行都不用改。
 */
public interface GarmentRecognizeService {

    /** 当前生效的模型名，用于落库留痕（如 ai_model 字段） */
    String modelName();

    /**
     * 识别单张图片中的主要服装
     *
     * @param imageBytes  图片原始字节
     * @param contentType 图片 MIME（如 image/jpeg），为空时按 image/jpeg 处理
     * @return 结构化识别结果（categoryL1 已归一化为 {@link GarmentCategory} 的 code）
     */
    GarmentRecognizeResult recognize(byte[] imageBytes, String contentType);
}
