package com.trybe.creator.util;

import cn.hutool.core.util.IdUtil;
import com.trybe.common.utils.FileTypeUtils;

/**
 * 创作者 OSS 目录路径构造工具（红线④：所有 OSS 路径必须走本类，禁止手拼字符串）
 *
 * <pre>
 * creator/{userId}/items/{itemId}/model_{uuid}.{ext}   # 作品图片（模特上身图，绑定时归位）
 * creator/{userId}/items/{itemNo}/origin.{ext}        # 物品主图原图
 * creator/{userId}/items/{itemNo}/cutout.png          # 透明底 PNG（试穿素材）
 * creator/{userId}/items/{itemNo}/ref_{uuid}.{ext}    # 参考图
 * creator/{userId}/models/{uuid}.{ext}                # 模特图（尚未绑定作品的暂存池）
 * creator/{userId}/profile/avatar.{ext}               # 创作者头像（个人中心，覆盖式归档）
 * creator/{userId}/profile/cover.{ext}                # 创作者主页封面图（个人中心，覆盖式归档）
 * creator/{userId}/tmp/{uuid}.{ext}                   # 预签名先写 tmp，confirm 后 moveObject 归档
 * creator/{userId}/items/pool/origin_{uuid}.{ext}     # 尚未绑定物品的物品图（临时池）
 * </pre>
 *
 * <p>设计原则：
 * <ul>
 *   <li>创作者维度隔离 —— 一级前缀 creator/{userId}，按人批量清理与越权防护（P2 强校验该前缀）</li>
 *   <li>业务维度分层 —— 二级前缀 items / models / tmp</li>
 *   <li>归属于具体物品的图片用 itemNo 建目录 —— 物品删除时可整目录清理</li>
 *   <li><b>作品图片按作品 id 归位</b> —— 上传时物品尚未创建，先落 models/ 暂存池，
 *       绑定（{@code POST /creator/items/{itemId}/model-images}）时 move 到
 *       {@code creator/{userId}/items/{itemId}/}，一个作品一个目录，可整目录清理</li>
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
     * 模特图归档位（<b>尚未绑定作品</b>的暂存池：上传时作品 id 还不存在，
     * 绑定时由 {@link #itemModelImage(Long, Long, String)} 归位到作品目录）
     */
    public static String modelImage(Long userId, String extension) {
        return String.format("creator/%d/models/%s.%s",
                userId, IdUtil.fastSimpleUUID(), FileTypeUtils.normalizeExtension(extension));
    }

    // ==================== 作品专属目录（按作品 id） ====================

    /**
     * 某个作品的专属目录前缀，如 {@code creator/7/items/123/}
     *
     * <p>作品 id 是数据库自增主键，纯数字；与业务编号 itemNo（CI 前缀）不同层语义，但同处 items/ 下，
     * 二者不可能撞名（一个是纯数字、一个是 CI+日期+序列）。
     */
    public static String itemPrefixOf(Long userId, Long itemId) {
        return String.format("creator/%d/items/%d/", userId, itemId);
    }

    /**
     * 作品图片（模特上身图）归位后的正式路径：{@code creator/{userId}/items/{itemId}/model_{uuid}.{ext}}
     *
     * <p>之所以带 uuid 后缀：一个作品有 1 或 4 张图，同目录内必须互不覆盖。
     */
    public static String itemModelImage(Long userId, Long itemId, String extension) {
        return String.format("creator/%d/items/%d/model_%s.%s",
                userId, itemId, IdUtil.fastSimpleUUID(), FileTypeUtils.normalizeExtension(extension));
    }

    /**
     * objectKey 是否已经在该作品的专属目录下（已归位则不必重复 move）
     */
    public static boolean isUnderItem(Long userId, Long itemId, String objectKey) {
        return userId != null && itemId != null && objectKey != null
                && objectKey.startsWith(itemPrefixOf(userId, itemId));
    }

    /**
     * 创作者头像归档位（个人中心；固定文件名覆盖式归档，换头像即覆盖同路径对象）
     */
    public static String profileAvatar(Long userId, String extension) {
        return String.format("creator/%d/profile/avatar.%s",
                userId, FileTypeUtils.normalizeExtension(extension));
    }

    /**
     * 创作者主页封面图归档位（个人中心；固定文件名覆盖式归档）
     */
    public static String profileCover(Long userId, String extension) {
        return String.format("creator/%d/profile/cover.%s",
                userId, FileTypeUtils.normalizeExtension(extension));
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
     * 校验 objectKey 是否为该创作者的<b>头像</b>归档位（{@code creator/{userId}/profile/avatar.{ext}}）。
     * 个人中心更新资料绑定头像时的强校验：头像唯一合法来源是 AVATAR 场景的 P2 确认归档，
     * 杜绝绑定他人对象、物品图等非头像用途对象。
     */
    public static boolean isProfileAvatar(Long userId, String objectKey) {
        return isProfileImage(userId, objectKey, "avatar");
    }

    /**
     * 校验 objectKey 是否为该创作者的<b>主页封面图</b>归档位（{@code creator/{userId}/profile/cover.{ext}}）。
     * 语义同 {@link #isProfileAvatar(Long, String)}。
     */
    public static boolean isProfileCover(Long userId, String objectKey) {
        return isProfileImage(userId, objectKey, "cover");
    }

    /**
     * 精确匹配个人中心图片归档位：key 必须与「前缀 + 文件名 + 白名单扩展名」重写的
     * 规范路径<b>完全相等</b>，天然拒绝大小写混杂、路径穿越、多余路径段与非图片扩展名
     */
    private static boolean isProfileImage(Long userId, String objectKey, String fileName) {
        if (userId == null || objectKey == null) {
            return false;
        }
        String ext = FileTypeUtils.extensionOf(objectKey);
        if (!FileTypeUtils.isSupportedImage(ext)) {
            return false;
        }
        return objectKey.equals(String.format("creator/%d/profile/%s.%s", userId, fileName, ext));
    }

    /**
     * itemNo 为空时用 pool 目录兜底，避免拼接出 items//origin.png 这类脏路径
     */
    private static String safeItemNo(String itemNo) {
        return (itemNo == null || itemNo.trim().isEmpty()) ? "pool" : itemNo.trim();
    }
}
