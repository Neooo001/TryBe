package com.trybe.common.service;


import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.OSSObject;

import com.trybe.common.config.OssProperties;
import com.trybe.common.utils.FileTypeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;


/**
 * 阿里云 OSS 统一读写/签名服务
 *
 * <p><b>多 bucket 约定</b>：项目同时使用两个存储空间（同 endpoint、同 AK/SK）——
 * <ul>
 *   <li>{@code aliyun.oss.bucket-name}（默认桶）：物品图、模特图、试穿素材等业务图片</li>
 *   <li>{@code aliyun.oss.bucket-name2}（第二桶）：头像、创作者主页封面图等个人形象类图片</li>
 * </ul>
 * 第二桶是<b>可选配置</b>：未配置时 {@link OssProperties#resolveSecondBucketName()}
 * 回落默认桶，行为等价于单桶。
 *
 * <p><b>调用侧红线</b>：同一个 objectKey 的「上传 / 归档 / 删除 / 取流 / 签名」
 * 必须传同一个 bucket（统一取 {@code con.resolveSecondBucketName()}），
 * 否则会出现「写进 A 桶、去 B 桶签名」从而 404/403 的隐蔽故障。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssService {

    private final OSS ossClient;
    private final OssProperties ossProperties;

    // ==================== bucket 解析 ====================

    /**
     * 默认 bucket（业务图片：物品图 / 模特图 / 试穿素材）
     */
    public String defaultBucketName() {
        return ossProperties.getBucketName();
    }

    /**
     * 第二 bucket（头像 / 主页封面图）；未配置时回落默认 bucket
     */
    public String secondBucketName() {
        return ossProperties.resolveSecondBucketName();
    }

    // ==================== 上传 ====================

    /**
     * 上传文件内容到 OSS（通用方法，供图片下载后转存等场景使用）
     *
     * @param content   文件内容输入流（调用方负责关闭）
     * @param objectKey 文件在OSS中的objectKey
     */
    public void putObject(InputStream content, String objectKey) {
        putObject(ossProperties.getBucketName(), content, objectKey);
    }

    /**
     * 上传文件内容到指定 bucket
     *
     * <p>容错：若显式传入的是「第二 bucket」但它尚未在控制台创建
     * （{@code NoSuchBucket}），自动回落默认 bucket 并告警 ——
     * 避免一处配置遗漏导致整条业务链路不可用。
     *
     * @param bucketName 目标 bucket；为空时使用默认 bucket
     * @param content    文件内容输入流（调用方负责关闭）
     * @param objectKey  文件在OSS中的objectKey
     */
    public void putObject(String bucketName, InputStream content, String objectKey) {
        String bucket = StrUtil.blankToDefault(bucketName, ossProperties.getBucketName());
        try {
            ossClient.putObject(bucket, objectKey, content);
        } catch (OSSException e) {
            if (!isFallbackableSecondBucket(bucket, e)) {
                throw e;
            }
            log.warn("[OssService] 第二 bucket({}) 不可用，已回落默认 bucket({}) 上传 objectKey={}；"
                            + "请检查 OSS 控制台是否已创建该 bucket。原因: {}",
                    bucket, ossProperties.getBucketName(), objectKey, e.getErrorMessage());
            ossClient.putObject(ossProperties.getBucketName(), objectKey, content);
            return;
        }
        log.debug("文件已上传: bucket={} objectKey={}", bucket, objectKey);
    }

    // ==================== MultipartFile 便捷上传 ====================

    /**
     * 上传 MultipartFile 到默认 bucket，使用给定 objectKey
     *
     * @param file      前端上传的文件
     * @param objectKey OSS 中的目标路径
     */
    public void uploadMultipart(MultipartFile file, String objectKey) {
        uploadMultipart(ossProperties.getBucketName(), file, objectKey);
    }

    /**
     * 上传 MultipartFile 到指定 bucket
     *
     * @param bucketName 目标 bucket；为空时使用默认 bucket
     * @param file       前端上传的文件
     * @param objectKey  OSS 中的目标路径
     */
    public void uploadMultipart(String bucketName, MultipartFile file, String objectKey) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(file.getBytes())) {
            putObject(bucketName, in, objectKey);
        } catch (IOException e) {
            throw new RuntimeException("上传文件到OSS失败: " + objectKey, e);
        }
    }

    /**
     * 上传用户头像到「第二 bucket」（头像专属桶），自动生成 objectKey
     *
     * <p>objectKey 规则：{@code avatars/{userId}/{时间戳}.{ext}}
     * 扩展名从原文件名推断，非法/缺失时回落 jpg。
     *
     * @param avatar 前端上传的头像文件
     * @param userId 当前用户 ID（用于路径隔离）
     * @return 保存到 OSS 的 objectKey（后续存 DB 用）
     */
    public String uploadAvatar(MultipartFile avatar, Long userId) {
        String ext = StrUtil.isNotBlank(avatar.getOriginalFilename())
                ? FileTypeUtils.extensionOf(avatar.getOriginalFilename())
                : FileTypeUtils.DEFAULT_IMAGE_EXTENSION;
        String objectKey = "avatars/" + userId + "/" + IdUtil.simpleUUID() + "." + ext;
        uploadMultipart(ossProperties.resolveSecondBucketName(), avatar, objectKey);
        return objectKey;
    }

    // ==================== 删除 / 移动 ====================

    /**
     * 删除 OSS 文件
     * @param objectName 文件在OSS中的完整路径
     */
    public void deleteFile(String objectName) {
        deleteFile(ossProperties.getBucketName(), objectName);
    }

    /**
     * 删除指定 bucket 中的 OSS 文件
     *
     * @param bucketName 目标 bucket；为空时使用默认 bucket
     * @param objectName 文件在OSS中的完整路径
     */
    public void deleteFile(String bucketName, String objectName) {
        String bucket = StrUtil.blankToDefault(bucketName, ossProperties.getBucketName());
        ossClient.deleteObject(bucket, objectName);
        log.info("文件已删除: bucket={} objectKey={}", bucket, objectName);
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
        moveObject(ossProperties.getBucketName(), sourceKey, targetKey);
    }

    /**
     * 在同一 bucket 内移动 OSS 文件（copy + delete）
     *
     * <p>注意：不支持跨 bucket 移动；确需跨桶请分别调用 put/get 与 delete。
     *
     * @param bucketName 目标 bucket；为空时使用默认 bucket
     * @param sourceKey  源 objectKey
     * @param targetKey  目标 objectKey
     */
    public void moveObject(String bucketName, String sourceKey, String targetKey) {
        String bucket = StrUtil.blankToDefault(bucketName, ossProperties.getBucketName());
        ossClient.copyObject(bucket, sourceKey, bucket, targetKey);
        try {
            ossClient.deleteObject(bucket, sourceKey);
            log.info("OSS文件已移动: bucket={} {} -> {}", bucket, sourceKey, targetKey);
        } catch (Exception e) {
            log.warn("[OssService] 移动后删除源文件失败，交由生命周期兜底 bucket={} sourceKey={}", bucket, sourceKey, e);
        }
    }

    // ==================== 读取 ====================

    /**
     * 从 OSS 获取文件输入流
     *
     * @param objectKey 文件在OSS中的objectKey
     * @return 文件输入流，调用方负责关闭
     */
    public InputStream getObjectContent(String objectKey) {
        return getObjectContent(ossProperties.getBucketName(), objectKey);
    }

    /**
     * 从指定 bucket 获取文件输入流
     *
     * @param bucketName 目标 bucket；为空时使用默认 bucket
     * @param objectKey  文件在OSS中的objectKey
     * @return 文件输入流，调用方负责关闭
     */
    public InputStream getObjectContent(String bucketName, String objectKey) {
        String bucket = StrUtil.blankToDefault(bucketName, ossProperties.getBucketName());
        OSSObject ossObject = ossClient.getObject(bucket, objectKey);
        return ossObject.getObjectContent();
    }

    /**
     * 读取指定 bucket 中对象的全部字节
     *
     * <p>对象存储的「读全量到内存」是常见需求（大小校验、图片解码、送模型），
     * 收敛到此避免各服务各写一份 try-with-resources 拷贝循环。
     *
     * @param bucketName 目标 bucket；为空时使用默认 bucket
     * @param objectKey  文件在OSS中的objectKey
     * @return 对象字节内容
     */
    public byte[] readObjectBytes(String bucketName, String objectKey) {
        try (InputStream in = getObjectContent(bucketName, objectKey)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int n;
            while ((n = in.read(data)) != -1) {
                buffer.write(data, 0, n);
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("读取OSS文件失败: " + objectKey, e);
        }
    }

    // ==================== 预签名 URL ====================

    /**
     * 生成预签名下载URL（使用配置的默认过期时间）
     *
     * @param objectKey 文件在OSS中的objectKey
     * @return 预签名URL字符串
     */
    public String generatePresignedUrl(String objectKey) {
        return generatePresignedUrl(ossProperties.getBucketName(), objectKey, getExpireSeconds());
    }

    /**
     * 生成指定 bucket 的预签名下载URL（使用配置的默认过期时间）
     *
     * @param bucketName 目标 bucket；为空时使用默认 bucket
     * @param objectKey  文件在OSS中的objectKey
     * @return 预签名URL字符串
     */
    public String generatePresignedUrl(String bucketName, String objectKey) {
        return generatePresignedUrl(bucketName, objectKey, getExpireSeconds());
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
        return generatePresignedPutUrl(ossProperties.getBucketName(), objectKey, expireSeconds, contentType);
    }

    /**
     * 生成指定 bucket 的预签名上传URL（PUT）
     *
     * <p>签名的 bucket 必须与实际接收 PUT 的 bucket 一致，否则返回 403 SignatureDoesNotMatch。
     *
     * @param bucketName    目标 bucket；为空时使用默认 bucket
     * @param objectKey     文件在OSS中的目标路径
     * @param expireSeconds 过期时间，单位秒
     * @param contentType   客户端 PUT 时的 Content-Type，可为 null
     * @return 预签名PUT上传URL字符串
     */
    public String generatePresignedPutUrl(String bucketName, String objectKey,
                                          long expireSeconds, String contentType) {
        String bucket = StrUtil.blankToDefault(bucketName, ossProperties.getBucketName());
        Date expiration = new Date(System.currentTimeMillis() + expireSeconds * 1000);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectKey, HttpMethod.PUT);
        request.setExpiration(expiration);
        if (StrUtil.isNotBlank(contentType)) {
            request.setContentType(contentType);
        }
        URL url = ossClient.generatePresignedUrl(request);
        log.debug("生成预签名上传URL: bucket={}, objectKey={}, contentType={}, expireSeconds={}",
                bucket, objectKey, contentType, expireSeconds);
        return url.toString();
    }

    /**
     * 生成预签名上传URL（使用配置的默认过期时间）
     */
    public String generatePresignedPutUrl(String objectKey) {
        return generatePresignedPutUrl(objectKey, getExpireSeconds());
    }

    /**
     * 生成指定 bucket 的预签名URL（自定义过期时间）
     *
     * @param bucketName    目标 bucket；为空时使用默认 bucket
     * @param objectKey     文件在OSS中的objectKey
     * @param expireSeconds 过期时间，单位秒
     * @return 预签名URL字符串
     */
    public String generatePresignedUrl(String bucketName, String objectKey, long expireSeconds) {
        String bucket = StrUtil.blankToDefault(bucketName, ossProperties.getBucketName());
        Date expiration = new Date(System.currentTimeMillis() + expireSeconds * 1000);
        URL url = ossClient.generatePresignedUrl(bucket, objectKey, expiration);
        log.debug("生成预签名URL: bucket={}, objectKey={}, expireSeconds={}", bucket, objectKey, expireSeconds);
        return url.toString();
    }

    /**
     * 生成预签名URL（自定义过期时间）
     *
     * @param objectKey     文件在OSS中的objectKey
     * @param expireSeconds 过期时间，单位秒
     * @return 预签名URL字符串
     */
    public String generatePresignedUrl(String objectKey, long expireSeconds) {
        return generatePresignedUrl(ossProperties.getBucketName(), objectKey, expireSeconds);
    }

    private long getExpireSeconds() {
        return ossProperties.getExpireSeconds() != null ? ossProperties.getExpireSeconds() : 3600L;
    }

    // ==================== 永久公开 URL ====================

    public String getFileUrl(String objectName) {
        return getFileUrl(ossProperties.getBucketName(), objectName);
    }

    /**
     * 生成指定 bucket 中文件的永久公开 URL（要求该 bucket 为公共读）
     *
     * @param bucketName 目标 bucket；为空时使用默认 bucket
     * @param objectName 文件在OSS中的objectKey
     */
    public String getFileUrl(String bucketName, String objectName) {
        String bucket = StrUtil.blankToDefault(bucketName, ossProperties.getBucketName());
        // 每个桶各自绑定自己的自定义域名：第二桶未单独配置域名时须走 OSS 默认域名，
        // 否则会把第二桶的对象拼到默认桶的域名下，生成一个必然 404 的地址
        String domain = ossProperties.domainOf(bucket);
        if (StrUtil.isNotBlank(domain)) {
            return domain + "/" + objectName;
        }
        return "https://" + bucket + "." + ossProperties.getEndpoint() + "/" + objectName;
    }

    /**
     * 生成对象访问 URL —— <b>对外统一入口，业务侧应优先用它</b>
     *
     * <p>按桶的读写权限自动选择 URL 形态：
     * <ul>
     *   <li>公共读 bucket（{@link OssProperties#isPublicReadBucket(String)}）→
     *       直接返回<b>永久公开 URL</b>，无签名、无过期时间，可被浏览器/CDN 缓存</li>
     *   <li>私有 bucket → 返回<b>预签名 URL</b>（带 Expires / Signature）</li>
     * </ul>
     *
     * <p>好处：桶权限调整时只改配置，业务代码不动；
     * 也不会出现「公共读桶还在算签名、签名过期导致裂图」这类问题。
     *
     * @param bucketName 目标 bucket；为空时使用默认 bucket
     * @param objectKey  文件在OSS中的objectKey；为空返回 null
     * @return 可直接访问的 URL；objectKey 为空时返回 null
     */
    public String accessUrl(String bucketName, String objectKey) {
        if (StrUtil.isBlank(objectKey)) {
            return null;
        }
        String bucket = StrUtil.blankToDefault(bucketName, ossProperties.getBucketName());
        if (ossProperties.isPublicReadBucket(bucket)) {
            return getFileUrl(bucket, objectKey);
        }
        return generatePresignedUrl(bucket, objectKey);
    }

    /**
     * 生成对象访问 URL 并附加版本号（{@code ?v=xxx}），用于打破浏览器/CDN 缓存
     *
     * <p><b>为什么需要</b>：头像 / 封面是「覆盖式归档」——换图时 objectKey 不变
     * （{@code creator/{uid}/profile/avatar.png}），对象被覆盖后 URL 与原图完全一致。
     * 桶改为公共读后下发的是永久 URL，URL 恒定 + OSS 默认不返回 Cache-Control
     * → 浏览器命中旧缓存，页面上看不到新头像。
     *
     * <p><b>为什么只在公共读桶生效</b>：预签名 URL 的 query 参数<b>参与签名计算</b>，
     * 擅自追加参数必然 403 SignatureDoesNotMatch。而预签名 URL 自带的
     * Expires/Signature 每次都在变，天然不会被缓存，无需版本号。
     * 因此这里对私有桶直接忽略 version。
     *
     * <p><b>version 必须是稳定值</b>（如记录的 update_time 毫秒数）：
     * 内容没变时版本号不变，缓存照常生效；内容变了版本号才变。
     * 切忌传 {@code System.currentTimeMillis()} 这类每次都变的值，会让缓存彻底失效。
     *
     * @param bucketName 目标 bucket；为空时使用默认 bucket
     * @param objectKey  文件在OSS中的objectKey
     * @param version    版本号；null 时不追加（私有桶无论传什么都忽略）
     * @return 可直接访问的 URL；objectKey 为空时返回 null
     */
    public String accessUrl(String bucketName, String objectKey, Long version) {
        String url = accessUrl(bucketName, objectKey);
        if (url == null || version == null) {
            return url;
        }
        if (!ossProperties.isPublicReadBucket(StrUtil.blankToDefault(bucketName, ossProperties.getBucketName()))) {
            return url;
        }
        return url + (url.indexOf('?') >= 0 ? "&v=" : "?v=") + version;
    }

    /**
     * 获取公共读bucket中文件的永久公开URL
     * 兼容DB中存储的两种格式：纯objectKey 或 完整URL
     *
     * @param avatarValue 数据库中存储的值（objectKey或完整URL）
     * @return 公开访问URL，如果为空则返回null
     */
    public String getPublicUrl(String avatarValue) {
        return getPublicUrl(ossProperties.getBucketName(), avatarValue);
    }

    /**
     * 获取<b>指定 bucket</b>中文件的永久公开URL（要求该 bucket 为公共读）
     *
     * @param bucketName  目标 bucket；为空时使用默认 bucket
     * @param avatarValue 数据库中存储的值（objectKey或完整URL）
     * @return 公开访问URL，如果为空则返回null
     */
    public String getPublicUrl(String bucketName, String avatarValue) {
        if (StrUtil.isBlank(avatarValue)) {
            return null;
        }
        if (avatarValue.startsWith("http")) {
            return avatarValue;
        }
        return getFileUrl(bucketName, avatarValue);
    }

    // ==================== 私有工具 ====================

    /**
     * 判断该异常是否属于「第二 bucket 未创建」且允许回落默认 bucket
     *
     * <p>两个前提同时成立才回落：① 传的确实是第二 bucket；② 配置里确实配了第二 bucket。
     * 这样在未配置第二桶（等价单桶）时不会改变任何既有行为。
     */
    private boolean isFallbackableSecondBucket(String bucket, OSSException e) {
        return ossProperties.hasSecondBucket()
                && bucket.equals(ossProperties.getBucketName2())
                && "NoSuchBucket".equals(e.getErrorCode());
    }
}