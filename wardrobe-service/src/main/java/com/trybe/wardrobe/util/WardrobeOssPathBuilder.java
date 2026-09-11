package com.trybe.wardrobe.util;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;

/**
 * 衣柜 OSS 目录路径构造工具
 *
 * <pre>
 * product/images/{bucket}/{num_iid}/main.jpg      # 淘宝导入主图（全局公共）
 * product/images/{bucket}/{num_iid}/detail_{i}.jpg # 详情图，i 从 1 开始
 * user/{userId}/wardrobe/custom/{uuid}.{ext}       # 手动添加
 * user/{userId}/wardrobe/outfits/{uuid}.{ext}      # 自定义穿搭
 * </pre>
 */
public final class WardrobeOssPathBuilder {

    private static final int HASH_BUCKET_MOD = 1000;

    private WardrobeOssPathBuilder() {
    }

    /**
     * 淘宝导入商品主图 objectKey
     */
    public static String productMain(Long numIid) {
        return String.format("product/images/%s/%d/main.jpg", hashBucket(numIid), numIid);
    }

    /**
     * 淘宝导入商品详情图 objectKey，index 从 1 开始
     */
    public static String productDetail(Long numIid, int index) {
        return String.format("product/images/%s/%d/detail_%d.jpg", hashBucket(numIid), numIid, index);
    }

    /**
     * 手动添加图片 objectKey
     */
    public static String custom(Long userId, String extension) {
        return String.format("user/%d/wardrobe/custom/%s.%s", userId, IdUtil.fastSimpleUUID(), normalizeExt(extension));
    }

    /**
     * 自定义穿搭图片 objectKey
     */
    public static String outfit(Long userId, String extension) {
        return String.format("user/%d/wardrobe/outfits/%s.%s", userId, IdUtil.fastSimpleUUID(), normalizeExt(extension));
    }

    private static String hashBucket(Long numIid) {
        long bucket = (numIid == null ? 0L : numIid) % HASH_BUCKET_MOD;
        return String.format("%03d", bucket);
    }

    private static String normalizeExt(String extension) {
        if (StrUtil.isBlank(extension)) {
            return "jpg";
        }
        return extension.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
