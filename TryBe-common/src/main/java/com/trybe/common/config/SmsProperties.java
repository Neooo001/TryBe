package com.trybe.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aliyun.sms")
public class SmsProperties {

    private String accessKeyId;
    private String accessKeySecret;
    private String templateParam;
    private String signName;
    private String templateCode;
    private String schemeName;

    /**
     * 验证码位数，取值 4~8（阿里云 dypnsapi 规范）；默认 4 位。
     * 未在 Nacos 配置时按 4 位下发，如需调整仅在 Nacos 增 `code-length` 即可，无需改代码。
     */
    private Long codeLength = 4L;
}