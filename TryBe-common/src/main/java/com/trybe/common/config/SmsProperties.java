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
}