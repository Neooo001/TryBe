package com.trybe.common.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 衣服识别大模型配置（wardrobe-service 与 creator-service 共用）
 *
 * <pre>
 * trybe:
 *   ai:
 *     garment:
 *       enabled: true
 *       provider: qwen-vl            # 当前支持 qwen-vl
 *       base-url: https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
 *       api-key: sk-xxx              # 只在 Nacos 配置中心填写，勿提交代码库
 *       model: qwen-vl-max           # 省钱可切 qwen-vl-plus
 *       timeout-ms: 30000
 *       max-image-bytes: 4194304     # 超过该大小先压缩再送模型
 * </pre>
 *
 * <p>两个服务都扫描 {@code com.trybe.common} 包，因此本 Bean 会自动注册，
 * 各自的 api-key 在各自 Nacos dataId 里配置即可。
 */
@Data
@Component
@ConfigurationProperties(prefix = "trybe.ai.garment")
public class GarmentAiProperties {

    /** 是否启用 AI 识别；关闭后调用方应走降级分支（如「仅保存原图 + 未分类草稿」） */
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
