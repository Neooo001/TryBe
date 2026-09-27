package com.trybe.common.config;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssProperties {

    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;
    private String domain;
    private Long expireSeconds;

    /**
     * 第二个 bucket（可选）：用于「头像 / 主页封面图」等个人形象类图片的独立存储。
     *
     * <p>对应配置项 {@code aliyun.oss.bucket-name2}（Nacos shared-oss.yaml）。
     * 与 {@link #bucketName} 同 endpoint、同 AK/SK，只是存储空间不同 ——
     * 因此无需再建一套 OSS 客户端与配置类，只要在读写时显式传入目标 bucket 名。
     *
     * <p><b>可选项语义</b>：未配置时 {@link #resolveSecondBucketName()} 回落到
     * {@link #bucketName}，行为与改造前完全一致（等价单桶）。这样本地/测试环境
     * 不配也能跑通，不会因为少一个配置项就启动失败或写错桶。
     */
    private String bucketName2;

    /**
     * 第二 bucket 是否为<b>公共读</b>（对应 {@code aliyun.oss.bucket-name2-public-read}）
     *
     * <p>头像 / 主页封面图是公开展示内容，桶设为公共读后可直接下发<b>永久 URL</b>，
     * 不必每次现算带签名的临时 URL —— 省掉签名计算、规避签名过期导致的图片裂图，
     * 也便于浏览器 / CDN 缓存。
     *
     * <p>默认 false：不配（或配 false）时行为与私有桶一致，仍走预签名 URL。
     * 这是<b>安全的回落方向</b> —— 桶权限与代码配置不一致时，最多是多算一次签名，
     * 不会把私有对象用公开 URL 暴露出去。
     */
    private Boolean bucketName2PublicRead;

    /**
     * 第二 bucket 绑定的自定义域名 / CDN 域名（可选，{@code aliyun.oss.bucket-name2-domain}）
     *
     * <p>未配置时走 OSS 默认域名 {@code https://{bucket}.{endpoint}/{key}}。
     */
    private String domain2;

    /**
     * 解析「第二 bucket」实际可用的 bucket 名
     *
     * @return 配置了 {@code bucket-name2} 时返回它，否则回落 {@link #bucketName}
     */
    public String resolveSecondBucketName() {
        return StrUtil.isBlank(bucketName2) ? bucketName : bucketName2;
    }

    /**
     * 是否真正启用了第二 bucket（用于调用方决定是否需要容错回流）
     */
    public boolean hasSecondBucket() {
        return StrUtil.isNotBlank(bucketName2);
    }

    /**
     * 判断指定 bucket 是否为「公共读」
     *
     * <p>只有「确实启用了第二 bucket」且「显式声明它公共读」且「传进来的正是第二 bucket」
     * 三个条件同时成立才返回 true。
     * 这样在未配置第二桶（等价单桶）时，默认桶不会被误判为公共读。
     *
     * @param bucketName 待判断的 bucket 名
     * @return true=可直接下发永久公开 URL；false=必须走预签名 URL
     */
    public boolean isPublicReadBucket(String bucketName) {
        if (StrUtil.isBlank(bucketName) || !hasSecondBucket()) {
            return false;
        }
        return Boolean.TRUE.equals(bucketName2PublicRead)
                && bucketName.equals(bucketName2);
    }

    /**
     * 取指定 bucket 绑定的自定义域名（未配置返回 null，调用方需自行回落到 OSS 默认域名）
     *
     * @param bucketName 目标 bucket 名
     * @return 自定义域名；第二桶未单独配置时返回 null
     */
    public String domainOf(String bucketName) {
        if (hasSecondBucket() && bucketName != null && bucketName.equals(bucketName2)) {
            return StrUtil.isBlank(domain2) ? null : domain2;
        }
        return StrUtil.isBlank(domain) ? null : domain;
    }
}
