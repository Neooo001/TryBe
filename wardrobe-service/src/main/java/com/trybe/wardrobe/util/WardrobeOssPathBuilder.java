package com.trybe.wardrobe.util;

import cn.hutool.core.util.IdUtil;
import com.trybe.common.utils.FileTypeUtils;

/**
 * 衣柜 OSS 目录路径构造工具
 *
 * <pre>
 * product/images/{bucket}/{num_iid}/main.jpg        # 淘宝导入主图（全局公共）
 * product/images/{bucket}/{num_iid}/detail_{i}.jpg  # 详情图，i 从 1 开始
 *
 * user/{userId}/wardrobe/import/{taskNo}/origin.{ext}  # AI 导入原图（上传即落地）
 * user/{userId}/wardrobe/import/{taskNo}/cutout.jpg    # AI 裁剪结果（bbox 矩形裁剪）
 * user/{userId}/wardrobe/import/{taskNo}/matting.jpg   # AI 抠图结果（像素级去背景 → 白底）
 * user/{userId}/wardrobe/items/{wardrobeId}/cover.jpg  # 条目正式封面（裁剪图归档）
 *
 * user/{userId}/wardrobe/custom/{uuid}.{ext}           # 手动添加
 * user/{userId}/wardrobe/outfits/{uuid}.{ext}          # 自定义穿搭
 * </pre>
 *
 * <p>设计原则：
 * <ul>
 *   <li>用户维度隔离 —— 一级前缀 user/{userId}，便于按用户批量清理与灰度</li>
 *   <li>业务维度分层 —— 二级前缀 wardrobe/{import|items|custom|outfits}</li>
 *   <li>导入过程用 taskNo 建目录 —— 处理失败可整目录删除，不留垃圾文件</li>
 *   <li>淘宝公共商品用 num_iid + 三位哈希桶 —— 散列均匀，规避单目录文件数过多</li>
 * </ul>
 */
public final class WardrobeOssPathBuilder {

    private static final int HASH_BUCKET_MOD = 1000;

    private WardrobeOssPathBuilder() {
    }

    // ==================== 淘宝商品（公共资源） ====================

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

    // ==================== AI 导入（本条功能核心） ====================

    /**
     * AI 导入原图 objectKey —— 用户上传的原始图片，上传请求内立即写入
     *
     * @param userId    用户ID
     * @param taskNo    导入任务业务号
     * @param extension 原文件扩展名
     */
    public static String importOrigin(Long userId, String taskNo, String extension) {
        return String.format("user/%d/wardrobe/import/%s/origin.%s",
                userId, taskNo, FileTypeUtils.normalizeExtension(extension));
    }

    /**
     * AI 裁剪结果 objectKey —— 异步任务成功后写入，统一 JPEG
     */
    public static String importCutout(Long userId, String taskNo) {
        return String.format("user/%d/wardrobe/import/%s/cutout.jpg", userId, taskNo);
    }

    /**
     * AI 抠图（去背景 → 白底）结果 objectKey —— 统一 JPEG
     *
     * <p>为什么与 {@link #importCutout} 用不同文件名：两者都是「裁剪产物」，
     * 但「像素级抠图」与「bbox 矩形裁剪」的呈现质量差异很大，前端角标需要如实区分
     * （已抠图 / AI裁剪）。不额外落库一个 mode 字段，是为了避免给 wardrobe_import_task
     * 加列——加列若未同步执行 SQL 会让所有导入直接失败，代价远高于收益；
     * 用「产物文件名」承载该语义，等价信息已经在库（cutout_object_key）里。
     */
    public static String importMatting(Long userId, String taskNo) {
        return String.format("user/%d/wardrobe/import/%s/matting.jpg", userId, taskNo);
    }

    /**
     * 判断裁剪产物是否为像素级抠图结果（供前端角标文案区分）
     */
    public static boolean isMattingCutout(String objectKey) {
        return objectKey != null && objectKey.endsWith("/matting.jpg");
    }

    /**
     * 条目正式封面 objectKey —— 用户确认入库后的归档位（可选，用于冻结历史版本）
     */
    public static String itemCover(Long userId, Long wardrobeId) {
        return String.format("user/%d/wardrobe/items/%d/cover.jpg", userId, wardrobeId);
    }

    /**
     * 整个导入任务的目录前缀 —— 删除任务时批量清理使用
     */
    public static String importDir(Long userId, String taskNo) {
        return String.format("user/%d/wardrobe/import/%s/", userId, taskNo);
    }

    // ==================== 手动添加 / 穿搭（沿用） ====================

    /**
     * 手动添加图片 objectKey
     */
    public static String custom(Long userId, String extension) {
        return String.format("user/%d/wardrobe/custom/%s.%s", userId, IdUtil.fastSimpleUUID(), FileTypeUtils.normalizeExtension(extension));
    }

    /**
     * 自定义穿搭图片 objectKey
     */
    public static String outfit(Long userId, String extension) {
        return String.format("user/%d/wardrobe/outfits/%s.%s", userId, IdUtil.fastSimpleUUID(), FileTypeUtils.normalizeExtension(extension));
    }

    // ==================== 私有方法 ====================

    private static String hashBucket(Long numIid) {
        long bucket = (numIid == null ? 0L : numIid) % HASH_BUCKET_MOD;
        return String.format("%03d", bucket);
    }

}
