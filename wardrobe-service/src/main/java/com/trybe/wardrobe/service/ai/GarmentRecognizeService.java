package com.trybe.wardrobe.service.ai;

import com.trybe.wardrobe.domain.dto.GarmentRecognizeResult;

/**
 * 衣服识别服务抽象
 *
 * <p>当前唯一实现为 {@link QwenVlGarmentRecognizeService}（阿里云百炼 Qwen-VL）。
 * 抽象出接口的目的是让供应商可替换：切换火山方舟 / 智谱 / 自建模型时，
 * 只需新增实现类并调整 {@code trybe.ai.garment.provider}，业务编排层零改动。
 */
public interface GarmentRecognizeService {

    /**
     * 识别图片中的服装，返回结构化属性 + 主体框
     *
     * @param imageBytes  图片二进制
     * @param contentType MIME 类型，如 image/jpeg
     * @return 识别结果；失败时抛异常，由调用方决定降级策略
     */
    GarmentRecognizeResult recognize(byte[] imageBytes, String contentType);

    /**
     * 当前使用的模型标识，落库到 wardrobe_item.ai_model 便于回溯
     */
    String modelName();
}
