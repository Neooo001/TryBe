package com.trybe.wardrobe.util;

import cn.hutool.core.util.IdUtil;
import com.trybe.common.utils.FileTypeUtils;

/**
 * 衣柜 OSS 目录路径构造工具
 *
 * <pre>
 * user/{userId}/wardrobe/import/{taskNo}/origin.{ext}  # AI 导入原图（上传即落地）
 * user/{userId}/wardrobe/import/{taskNo}/cutout.jpg    # AI 裁剪结果（bbox 矩形裁剪）
 * user/{userId}/wardrobe/import/{taskNo}/matting.jpg   # AI 抠图结果（像素级去背景 → 白底）
 * user/{userId}/wardrobe/import/{taskNo}/matting-src.jpg # 送云端分割前的临时压缩图（用完即删）
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
 * </ul>
 */
public final class WardrobeOssPathBuilder {

    private WardrobeOssPathBuilder() {
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
     * AI 抠图（去背景 → 白底留图）结果 objectKey —— 统一 JPEG
     *
     * <p>抠图服务返回的是带 alpha 的前景图，上传前会先合成为<b>纯白底</b>再存（见 {@code WhiteBackdrop}）。
     * 白底之后不再需要 alpha，用 JPEG 存能把体积压到 PNG 的 1/5 ~ 1/10 ——
     * 衣橱列表一次 12 张，这个差别直接决定「秒开」还是「转圈」。
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
     * 送云端服饰分割前的临时压缩图 objectKey —— 原图超限（&gt;3MB 或边长 ≥3000px）时才生成，
     * 调用结束（无论成功失败）都会删除，不参与任何业务语义。
     */
    public static String importMattingSource(Long userId, String taskNo) {
        return String.format("user/%d/wardrobe/import/%s/matting-src.jpg", userId, taskNo);
    }

    /**
     * 判断裁剪产物是否为像素级抠图结果（供前端角标文案区分）
     *
     * <p>同时兼容历史数据：2026-10-24 短暂有过透明底版本，产物名为 {@code matting.png}，
     * 这些已有任务的 key 仍指向 .png，不能因为改名而认不出来。
     */
    public static boolean isMattingCutout(String objectKey) {
        return objectKey != null
                && (objectKey.endsWith("/matting.png") || objectKey.endsWith("/matting.jpg"));
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

}
