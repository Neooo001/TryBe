package com.trybe.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wechat.miniprogram")
public class WechatProperties {
    /**
     * 小程序APPID
     */
    private String appId;
    /**
     * 小程序APP密钥
     */
    private String appSecret;
}