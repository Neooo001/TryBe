package com.trybe.common.service;


import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.OSSObject;

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
     * 上传文件内容到 OSS（通用方法，供图片下载后转存等场景使用）
     *
     * @param content   文件内容输入流（调用方负责关闭）
     * @param objectKey 文件在OSS中的objectKey
     */
    public void putObject(InputStream content, String objectKey) {
        ossClient.putObject(ossProperties.getBucketName(), objectKey, content);
        log.debug("文件已上传: objectKey={}", objectKey);
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
     * 移动 OSS 文件（OSS 无原生 rename，等价于 copy + delete）
     *
     * <p>容错策略：copy 失败不删源文件；copy 成功后 delete 失败仅告警，
     * 源文件留给 OSS 生命周期规则兜底回收。
     *
     * @param sourceKey 源 objectKey
     * @param targetKey 目标 objectKey
     */
    public void moveObject(String sourceKey, String targetKey) {
        String bucket = ossProperties.getBucketName();
        ossClient.copyObject(bucket, sourceKey, bucket, targetKey);
        try {
            ossClient.deleteObject(bucket, sourceKey);
            log.info("OSS文件已移动: {} -> {}", sourceKey, targetKey);
        } catch (Exception e) {
            log.warn("[OssService] 移动后删除源文件失败，交由生命周期兜底 sourceKey={}", sourceKey, e);
        }
    }

    /**
     * 从 OSS 获取文件输入流
     *
     * @param objectKey 文件在OSS中的objectKey
     * @return 文件输入流，调用方负责关闭
     */
    public InputStream getObjectContent(String objectKey) {
        OSSObject ossObject = ossClient.getObject(ossProperties.getBucketName(), objectKey);
        return ossObject.getObjectContent();
    }

    /**
     * 生成预签名下载URL（使用配置的默认过期时间）
     *
     * @param objectKey 文件在OSS中的objectKey
     * @return 预签名URL字符串
     */
    public String generatePresignedUrl(String objectKey) {
        return generatePresignedUrl(objectKey, getExpireSeconds());
    }

    /**
     * 生成预签名上传URL — 客户端直传OSS使用PUT方法
     *
     * @param objectKey     文件在OSS中的目标路径
     * @param expireSeconds 过期时间，单位秒
     * @return 预签名PUT上传URL字符串
     */
    public String generatePresignedPutUrl(String objectKey, long expireSeconds) {
        return generatePresignedPutUrl(objectKey, expireSeconds, null);
    }

    /**
     * 生成预签名上传URL — 客户端直传OSS使用PUT方法（指定Content-Type）
     *
     * <p>OSS V1 签名中 Content-Type 参与签名计算，若客户端 PUT 时携带了
     * Content-Type 请求头，签名时必须设置一致的 Content-Type，否则会返回
     * 403 SignatureDoesNotMatch。
     *
     * @param objectKey     文件在OSS中的目标路径
     * @param contentType   客户端 PUT 时的 Content-Type，可为 null
     * @return 预签名PUT上传URL字符串
     */
    public String generatePresignedPutUrl(String objectKey, String contentType) {
        return generatePresignedPutUrl(objectKey, getExpireSeconds(), contentType);
    }

    /**
     * 生成预签名上传URL — 客户端直传OSS使用PUT方法（指定Content-Type与过期时间）
     */
    public String generatePresignedPutUrl(String objectKey, long expireSeconds, String contentType) {
        Date expiration = new Date(System.currentTimeMillis() + expireSeconds * 1000);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                ossProperties.getBucketName(), objectKey, HttpMethod.PUT);
        request.setExpiration(expiration);
        if (StrUtil.isNotBlank(contentType)) {
            request.setContentType(contentType);
        }
        URL url = ossClient.generatePresignedUrl(request);
        log.debug("生成预签名上传URL: objectKey={}, contentType={}, expireSeconds={}",
                objectKey, contentType, expireSeconds);
        return url.toString();
    }

    /**
     * 生成预签名上传URL（使用配置的默认过期时间）
     */
    public String generatePresignedPutUrl(String objectKey) {
        return generatePresignedPutUrl(objectKey, getExpireSeconds());
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

    public String getFileUrl(String objectName) {
        if (StrUtil.isNotBlank(ossProperties.getDomain())) {
            return ossProperties.getDomain() + "/" + objectName;
        }
        return "https://" + ossProperties.getBucketName() + "." + ossProperties.getEndpoint() + "/" + objectName;
    }

    /**
     * 获取公共读bucket中文件的永久公开URL
     * 兼容DB中存储的两种格式：纯objectKey 或 完整URL
     *
     * @param avatarValue 数据库中存储的值（objectKey或完整URL）
     * @return 公开访问URL，如果为空则返回null
     */
    public String getPublicUrl(String avatarValue) {
        if (StrUtil.isBlank(avatarValue)) {
            return null;
        }
        if (avatarValue.startsWith("http")) {
            return avatarValue;
        }
        return getFileUrl(avatarValue);
    }
}