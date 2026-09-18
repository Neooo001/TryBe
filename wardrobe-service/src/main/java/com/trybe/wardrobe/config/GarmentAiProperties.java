package com.trybe.wardrobe.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 衣服识别大模型配置
 *
 * <pre>
 * trybe:
 *   ai:
 *     garment:
 *       enabled: true
 *       provider: qwen-vl            # 当前支持 qwen-vl
 *       base-url: https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
 *       api-key: sk-xxx
 *       model: qwen-vl-max           # 省钱可切 qwen-vl-plus
 *       timeout-ms: 30000
 *       max-image-bytes: 4194304     # 超过该大小先压缩再送模型
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "trybe.ai.garment")
public class GarmentAiProperties {

    /** 是否启用 AI 识别；关闭后导入流程退化为「仅保存原图 + 未分类草稿」 */
    private Boolean enabled = true;

    /** 供应商标识，预留多实现切换 */
    private String provider = "qwen-vl";

    /** OpenAI 兼容 chat/completions 地址 */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    /** 百炼 API Key（建议走 Nacos 配置中心注入，勿硬编码） */
    private String apiKey;

    /** 视觉模型名：qwen-vl-max / qwen-vl-plus / qwen-vl-max-latest */
    private String model = "qwen-vl-max";

    /** 单次调用超时（毫秒） */
    private Integer timeoutMs = 30000;

    /** 送模型的图片最大字节数，超出先等比压缩 */
    private Integer maxImageBytes = 4194304;
}
