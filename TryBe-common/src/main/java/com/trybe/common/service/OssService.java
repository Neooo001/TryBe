package com.trybe.common.service;


import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.OSS;

import com.trybe.common.config.OssProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;


@Slf4j
@Service
@RequiredArgsConstructor
public class OssService {

    private final OSS ossClient;
    private final OssProperties ossProperties;


    /**
     * 上传用户头像到 OSS
     * 路径格式: avatar/{userId}/original.{ext}
     *
     * @param file   头像文件
     * @param userId 用户ID
     * @return 文件在OSS中的objectKey
     */
    public String uploadAvatar(MultipartFile file, Long userId) {
        String originalFilename = file.getOriginalFilename();
        String extension = StrUtil.subAfter(originalFilename, ".", true);
        String objectName = "avatar/" + userId + "/original." + extension;

        try (InputStream inputStream = file.getInputStream()) {
            ossClient.putObject(ossProperties.getBucketName(), objectName, inputStream);
            log.info("头像上传成功: {}", objectName);
            return objectName;
        } catch (IOException e) {
            log.error("头像上传失败", e);
            throw new RuntimeException("头像上传失败", e);
        }
    }

    /**
     * 删除 OSS 文件
     * @param objectName 文件在OSS中的完整路径
     */
    public void deleteFile(String objectName) {
        ossClient.deleteObject(ossProperties.getBucketName(), objectName);
        log.info("文件已删除: {}", objectName);
    }

    /**
     * 生成预签名URL（使用配置的默认过期时间）
     *
     * @param objectKey 文件在OSS中的objectKey
     * @return 预签名URL字符串
     */
    public String generatePresignedUrl(String objectKey) {
        return generatePresignedUrl(objectKey, getExpireSeconds());
    }

    /**
     * 生成预签名URL（自定义过期时间）
     *
     * @param objectKey     文件在OSS中的objectKey
     * @param expireSeconds 过期时间，单位秒
     * @return 预签名URL字符串
     */
    public String generatePresignedUrl(String objectKey, long expireSeconds) {
        Date expiration = new Date(System.currentTimeMillis() + expireSeconds * 1000);
        URL url = ossClient.generatePresignedUrl(ossProperties.getBucketName(), objectKey, expiration);
        log.debug("生成预签名URL: objectKey={}, expireSeconds={}", objectKey, expireSeconds);
        return url.toString();
    }

    private long getExpireSeconds() {
        return ossProperties.getExpireSeconds() != null ? ossProperties.getExpireSeconds() : 3600L;
    }

    private String getFileUrl(String objectName) {
        if (StrUtil.isNotBlank(ossProperties.getDomain())) {
            return ossProperties.getDomain() + "/" + objectName;
        }
        return "https://" + ossProperties.getBucketName() + "." + ossProperties.getEndpoint() + "/" + objectName;
    }
}