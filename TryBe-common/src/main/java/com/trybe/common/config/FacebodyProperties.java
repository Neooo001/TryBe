package com.trybe.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aliyun.facebody")
public class FacebodyProperties {

    private String accessKeyId;
    private String accessKeySecret;
    private String endpoint = "facebody.cn-shanghai.aliyuncs.com";
    private String regionId = "cn-shanghai";
}