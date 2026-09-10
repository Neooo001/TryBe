package com.trybe.common.config;

import com.baidu.aip.bodyanalysis.AipBodyAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p>
 * 百度智能云手部关键点检测客户端配置
 * </p>
 *
 * <p>创建 {@link AipBodyAnalysis} Bean，用于调用百度人体分析 API（手部关键点检测）。
 * 仅在 classpath 中存在 AipBodyAnalysis 时自动装配，避免缺少 SDK 依赖时启动失败。
 */
@Slf4j
@Configuration
@ConditionalOnClass(AipBodyAnalysis.class)
@RequiredArgsConstructor
public class BaiduHandConfig {

    private final BaiduHandProperties baiduHandProperties;

    /**
     * 创建百度人体分析客户端 Bean
     *
     * <p>使用百度 AI SDK 的 {@link AipBodyAnalysis}，通过 AppId、API Key、Secret Key
     * 进行初始化认证，SDK 内部自动管理 access_token 的获取与刷新。
     *
     * @return AipBodyAnalysis 客户端实例
     */
    @Bean
    public AipBodyAnalysis aipBodyAnalysis() {
        AipBodyAnalysis client = new AipBodyAnalysis(
                baiduHandProperties.getAppId(),
                baiduHandProperties.getApiKey(),
                baiduHandProperties.getSecretKey()
        );
        log.info("[BaiduHandConfig] 初始化百度人体分析客户端 appId={}", baiduHandProperties.getAppId());
        return client;
    }
}