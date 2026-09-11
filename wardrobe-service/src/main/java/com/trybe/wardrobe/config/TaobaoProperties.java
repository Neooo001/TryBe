package com.trybe.wardrobe.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "taobao.tbk")
public class TaobaoProperties {

    /** 淘宝开放平台应用 AppKey */
    private String appKey;

    /** 应用 AppSecret */
    private String appSecret;

    /** 网关地址 */
    private String endpoint = "https://eco.taobao.com/router/rest";
}
