package com.trybe.common.config;

import com.aliyun.facebody20191230.Client;
import com.aliyun.teaopenapi.models.Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnClass(Client.class)
@RequiredArgsConstructor
public class FacebodyConfig {

    private final FacebodyProperties facebodyProperties;

    @Bean
    public Client facebodyClient() throws Exception {
        Config config = new Config()
                .setAccessKeyId(facebodyProperties.getAccessKeyId())
                .setAccessKeySecret(facebodyProperties.getAccessKeySecret())
                .setEndpoint(facebodyProperties.getEndpoint());
        log.info("[FacebodyConfig] 初始化阿里云人脸识别客户端 endpoint={}", facebodyProperties.getEndpoint());
        return new Client(config);
    }
}