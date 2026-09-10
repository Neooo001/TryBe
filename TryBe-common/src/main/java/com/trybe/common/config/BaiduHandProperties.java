package com.trybe.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * <p>
 * 百度智能云手部关键点检测配置属性
 * </p>
 *
 * <p>配置来源：Nacos 共享配置 Baidu-hand-keypoints-keys.yaml，
 * 配置前缀为 {@code baidu.hand}。
 *
 * <p>示例配置：
 * <pre>{@code
 * baidu:
 *   hand:
 *     app-id: 你的AppId
 *     api-key: 你的APIKey
 *     secret-key: 你的SecretKey
 * }</pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "baidu.hand")
public class BaiduHandProperties {

    /** 百度智能云应用 AppId */
    private String appId;

    /** 百度智能云应用 API Key */
    private String apiKey;

    /** 百度智能云应用 Secret Key */
    private String secretKey;
}