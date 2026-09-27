package com.trybe.creator.service;

import cn.hutool.core.util.StrUtil;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BadRequestException;
import com.trybe.common.exception.ForbiddenException;
import com.trybe.common.service.OssService;
import com.trybe.common.utils.FileTypeUtils;
import com.trybe.common.utils.ImageUtils;
import com.trybe.creator.domain.dto.PresignReq;
import com.trybe.creator.domain.dto.UploadConfirmReq;
import com.trybe.creator.domain.enums.UploadScene;
import com.trybe.creator.domain.vo.PresignVO;
import com.trybe.creator.domain.vo.UploadConfirmVO;
import com.trybe.creator.util.CreatorOssPathBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 创作者图片上传编排服务（P1 预签名 / P2 确认归档）
 *
 * <p>流程：P1 生成 tmp 位预签名 PUT URL → 前端直传 OSS → P2 confirm 校验后 moveObject 归档。
 * <b>本类含 OSS 外部交互，按红线① 不加 @Transactional。</b>
 *
 * <p><b>多 bucket 约定</b>：头像 / 主页封面图（{@link UploadScene#isProfileImageScene()}）
 * 走第二 bucket（{@code aliyun.oss.bucket-name2}），其余场景走默认 bucket。
 * P1 写 tmp、P2 读 tmp、P2 move 归档三处 bucket 必须一致，
 * 故统一由 {@link #resolveBucket(UploadScene)} 决定，禁止各分支各写一遍。
 *
 * <p>安全约定：P2 强校验 objectKey 前缀 {@code creator/{userId}/}，
 * 防止构造他人的 objectKey 越权确认/挪动文件。
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadService {

    /** 预签名 URL 有效期（秒） */
    private static final long PRESIGN_EXPIRE_SECONDS = 600L;
    /** 单张图片大小上限 10MB */
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    private final OssService ossService;

    /**
     * 场景 → 目标 bucket：个人形象类（头像/封面）走第二 bucket，其余走默认 bucket
     */
    private String resolveBucket(UploadScene scene) {
        return scene.isProfileImageScene()
                ? ossService.secondBucketName()
                : ossService.defaultBucketName();
    }

    /**
     * P1 预签名直传
     *
     * @param userId 当前创作者用户ID（来自 UserContext）
     * @param req    场景与扩展名
     * @return 预签名信息（objectKey 为 tmp 临时位）
     */
    public PresignVO presign(Long userId, PresignReq req) {
        UploadScene scene = UploadScene.strictOf(req.getScene());
        String ext = scene.normalizeExtension(req.getExt());
        String objectKey = CreatorOssPathBuilder.tmp(userId, ext);
        String bucket = resolveBucket(scene);
        String uploadUrl = ossService.generatePresignedPutUrl(
                bucket, objectKey, PRESIGN_EXPIRE_SECONDS, FileTypeUtils.contentTypeOf(ext));
        // 日志脱敏：绝不打印预签名 URL
        log.info("[upload] 预签名生成 userId={} scene={} bucket={} objectKey={}",
                userId, scene.getCode(), bucket, objectKey);
        return new PresignVO(objectKey, uploadUrl, PRESIGN_EXPIRE_SECONDS);
    }

    /**
     * P2 上传确认：存在性/大小/类型校验 + 透明底兜底校验 + 归档到正式位
     *
     * @param userId 当前创作者用户ID
     * @param req    objectKey 与场景
     * @return 归档后的访问信息
     */
    public UploadConfirmVO confirm(Long userId, UploadConfirmReq req) {
        UploadScene scene = UploadScene.strictOf(req.getScene());
        String bucket = resolveBucket(scene);
        String sourceKey = req.getObjectKey();
        if (!CreatorOssPathBuilder.belongsTo(userId, sourceKey)) {
            throw new ForbiddenException(ErrorCodeEnum.FORBIDDEN, "无权访问该文件");
        }

        byte[] bytes;
        try {
            bytes = ossService.readObjectBytes(bucket, sourceKey);
        } catch (Exception e) {
            log.warn("[upload] 读取OSS文件失败 userId={} scene={} bucket={} objectKey={}",
                    userId, scene.getCode(), bucket, sourceKey, e);
            throw new BadRequestException("文件不存在或尚未上传完成");
        }
        if (bytes.length == 0) {
            throw new BadRequestException("文件内容为空");
        }
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new BadRequestException("图片大小不能超过10MB");
        }

        String ext = FileTypeUtils.extensionOf(sourceKey);
        if (!FileTypeUtils.isSupportedImage(ext)) {
            throw new BadRequestException("不支持的图片格式，仅支持 " + FileTypeUtils.supportedImageExtensions());
        }

        BufferedImage image = ImageUtils.read(bytes);
        if (image == null) {
            throw new BadRequestException("无法解析图片，请检查文件格式是否损坏");
        }

        String targetKey = resolveTargetKey(userId, scene, req.getItemNo(), ext);
        if (!targetKey.equals(sourceKey)) {
            ossService.moveObject(bucket, sourceKey, targetKey);
        }

        UploadConfirmVO vo = new UploadConfirmVO();
        vo.setObjectKey(targetKey);
        // 公共读 bucket（头像/封面所在的第二桶）下发永久 URL，私有 bucket 下发预签名 URL。
        // 传当前时间戳作版本号：换图是「同 key 覆盖」，URL 不变会被浏览器缓存住，
        // 前端拿到的预览地址若与旧地址字符串相同，`<img>` 根本不会重新请求。
        // 预签名 URL 分支会自动忽略该版本号（query 参与签名，追加会 403）。
        vo.setAccessUrl(ossService.accessUrl(bucket, targetKey, System.currentTimeMillis()));
        vo.setWidth(image.getWidth());
        vo.setHeight(image.getHeight());
        if (scene == UploadScene.ITEM_CUTOUT) {
            // 后端兜底透明底校验（前端已主做）：只提示，不阻断
            vo.setHasAlpha(hasTransparentPixels(image));
        }
        log.info("[upload] 上传已确认 userId={} scene={} bucket={} targetKey={} size={}x{}",
                userId, scene.getCode(), bucket, targetKey, image.getWidth(), image.getHeight());
        return vo;
    }

    /**
     * 按场景与 itemNo 计算归档位（未绑定物品时归档到 pool 目录）
     */
    private String resolveTargetKey(Long userId, UploadScene scene, String itemNo, String ext) {
        boolean boundToItem = StrUtil.isNotBlank(itemNo);
        switch (scene) {
            case ITEM_ORIGIN:
                return boundToItem ? CreatorOssPathBuilder.itemOrigin(userId, itemNo, ext)
                        : CreatorOssPathBuilder.poolOrigin(userId, ext);
            case ITEM_CUTOUT:
                return boundToItem ? CreatorOssPathBuilder.itemCutout(userId, itemNo)
                        : CreatorOssPathBuilder.poolCutout(userId);
            case ITEM_REFERENCE:
                return boundToItem ? CreatorOssPathBuilder.itemReference(userId, itemNo, ext)
                        : CreatorOssPathBuilder.poolReference(userId, ext);
            case MODEL_IMAGE:
                return CreatorOssPathBuilder.modelImage(userId, ext);
            case AVATAR:
                return CreatorOssPathBuilder.profileAvatar(userId, ext);
            case COVER:
                return CreatorOssPathBuilder.profileCover(userId, ext);
            default:
                throw new BadRequestException("无效的上传场景: " + scene.getCode());
        }
    }

    /**
     * 透明通道检测：采样四角 + 四边中点 + 中心，任一采样点 alpha < 10 认为含透明区域
     */
    private boolean hasTransparentPixels(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[][] points = new int[][]{
                {0, 0}, {width - 1, 0}, {0, height - 1}, {width - 1, height - 1},
                {width / 2, 0}, {width / 2, height - 1}, {0, height / 2}, {width - 1, height / 2},
                {width / 2, height / 2}
        };
        for (int[] point : points) {
            int argb = image.getRGB(point[0], point[1]);
            int alpha = (argb >>> 24) & 0xff;
            if (alpha < 10) {
                return true;
            }
        }
        return image.getColorModel().hasAlpha();
    }

    private byte[] readAllBytes(String objectKey) throws IOException {
        try (InputStream in = ossService.getObjectContent(objectKey)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int n;
            while ((n = in.read(data)) != -1) {
                buffer.write(data, 0, n);
            }
            return buffer.toByteArray();
        }
    }
}
