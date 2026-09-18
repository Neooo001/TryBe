package com.trybe.creator.util;

import cn.hutool.core.util.IdUtil;
import com.trybe.common.utils.FileTypeUtils;

/**
 * 创作者 OSS 目录路径构造工具（红线④：所有 OSS 路径必须走本类，禁止手拼字符串）
 *
 * <pre>
 * creator/{userId}/items/{itemNo}/origin.{ext}        # 物品主图原图
 * creator/{userId}/items/{itemNo}/cutout.png          # 透明底 PNG（试穿素材）
 * creator/{userId}/items/{itemNo}/ref_{uuid}.{ext}    # 参考图
 * creator/{userId}/models/{uuid}.{ext}                # 模特图
 * creator/{userId}/tmp/{uuid}.{ext}                   # 预签名先写 tmp，confirm 后 moveObject 归档
 * creator/{userId}/items/pool/origin_{uuid}.{ext}     # 尚未绑定物品的物品图（临时池）
 * </pre>
 *
 * <p>设计原则：
 * <ul>
 *   <li>创作者维度隔离 —— 一级前缀 creator/{userId}，按人批量清理与越权防护（P2 强校验该前缀）</li>
 *   <li>业务维度分层 —— 二级前缀 items / models / tmp</li>
 *   <li>归属于具体物品的图片用 itemNo 建目录 —— 物品删除时可整目录清理</li>
 * </ul>
 */
public final class CreatorOssPathBuilder {

    private CreatorOssPathBuilder() {
    }

    // ==================== 临时上传位（P1 presign） ====================

    /**
     * 预签名上传的临时 objectKey —— 前端 PUT 直传到此，confirm 通过后 moveObject 归档
     */
    public static String tmp(Long userId, String extension) {
        return String.format("creator/%d/tmp/%s.%s",
                userId, IdUtil.fastSimpleUUID(), FileTypeUtils.normalizeExtension(extension));
    }

    // ==================== 正式归档位（P2 confirm 后 move） ====================

    /**
     * 物品主图原图归档位
     */
    public static String itemOrigin(Long userId, String itemNo, String extension) {
        return String.format("creator/%d/items/%s/origin.%s",
                userId, safeItemNo(itemNo), FileTypeUtils.normalizeExtension(extension));
    }

    /**
     * 透明底 PNG 归档位（强制 png）
     */
    public static String itemCutout(Long userId, String itemNo) {
        return String.format("creator/%d/items/%s/cutout.png", userId, safeItemNo(itemNo));
    }

    /**
     * 参考图归档位
     */
    public static String itemReference(Long userId, String itemNo, String extension) {
        return String.format("creator/%d/items/%s/ref_%s.%s",
                userId, safeItemNo(itemNo), IdUtil.fastSimpleUUID(), FileTypeUtils.normalizeExtension(extension));
    }

    /**
     * 模特图归档位（模特图不挂在某个 itemNo 下：一张模特图可服务于多个配搭场景）
     */
    public static String modelImage(Long userId, String extension) {
        return String.format("creator/%d/models/%s.%s",
                userId, IdUtil.fastSimpleUUID(), FileTypeUtils.normalizeExtension(extension));
    }

    // ==================== 未绑定物品的临时池 ====================

    /**
     * 尚未绑定 itemNo 的物品主图（先上传后创建物品的场景）
     */
    public static String poolOrigin(Long userId, String extension) {
        return String.format("creator/%d/items/pool/origin_%s.%s",
                userId, IdUtil.fastSimpleUUID(), FileTypeUtils.normalizeExtension(extension));
    }

    /**
     * 尚未绑定 itemNo 的透明底 PNG
     */
    public static String poolCutout(Long userId) {
        return String.format("creator/%d/items/pool/cutout_%s.png", userId, IdUtil.fastSimpleUUID());
    }

    /**
     * 尚未绑定 itemNo 的参考图
     */
    public static String poolReference(Long userId, String extension) {
        return String.format("creator/%d/items/pool/ref_%s.%s",
                userId, IdUtil.fastSimpleUUID(), FileTypeUtils.normalizeExtension(extension));
    }

    // ==================== 路径校验 ====================

    /**
     * 指定创作者的私有空间前缀（越权校验唯一来源）
     */
    public static String prefixOf(Long userId) {
        return "creator/" + userId + "/";
    }

    /**
     * 判断 objectKey 是否属于该创作者的私有空间（P2 防越权强校验）
     */
    public static boolean belongsTo(Long userId, String objectKey) {
        return userId != null && objectKey != null && objectKey.startsWith(prefixOf(userId));
    }

    /**
     * itemNo 为空时用 pool 目录兜底，避免拼接出 items//origin.png 这类脏路径
     */
    private static String safeItemNo(String itemNo) {
        return (itemNo == null || itemNo.trim().isEmpty()) ? "pool" : itemNo.trim();
    }
}
