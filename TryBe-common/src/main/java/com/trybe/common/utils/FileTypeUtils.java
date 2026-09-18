package com.trybe.common.utils;

import cn.hutool.core.util.StrUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * <p>
 * 文件类型工具 —— 扩展名规范化、图片类型白名单、MIME 推断
 * </p>
 *
 * <p>抽取理由：同一套逻辑此前在三个地方各写了一遍
 * （OSS 路径构造、上传接口校验、送模型的 content-type 推断），
 * 且白名单内容并不一致 —— 上传接口放行 webp，推断逻辑却不知道 webp，
 * 这类「校验与实际使用不同源」的 bug 排查成本极高。
 *
 * <p>安全约定：扩展名一律「小写 + 只保留字母数字」，
 * 天然阻断 {@code .jpg/../../etc} 这类路径穿越与双扩展名注入。
 */
public final class FileTypeUtils {

    /** 允许的图片扩展名（含小程序 chooseMedia 常见输出格式） */
    private static final Set<String> IMAGE_EXTENSIONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("jpg", "jpeg", "png", "webp", "bmp", "heic")));

    /** 扩展名缺失或非法时的兜底值 */
    public static final String DEFAULT_IMAGE_EXTENSION = "jpg";

    private FileTypeUtils() {
    }

    /**
     * 从文件名提取并规范化扩展名
     *
     * <p>取最后一个点之后的内容，因此 {@code a.tar.gz} 得到 {@code gz}；
     * 无扩展名时返回 {@link #DEFAULT_IMAGE_EXTENSION}。
     */
    public static String extensionOf(String filename) {
        if (StrUtil.isBlank(filename)) {
            return DEFAULT_IMAGE_EXTENSION;
        }
        String ext = StrUtil.subAfter(filename, ".", true);
        return normalizeExtension(ext);
    }

    /**
     * 规范化扩展名：小写化，只保留 [a-z0-9]
     *
     * @param extension 原始扩展名（可带前导点、可大写）
     * @return 规范化结果；为空或含非法字符导致被清空时返回默认值
     */
    public static String normalizeExtension(String extension) {
        if (StrUtil.isBlank(extension)) {
            return DEFAULT_IMAGE_EXTENSION;
        }
        String ext = extension.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return ext.isEmpty() ? DEFAULT_IMAGE_EXTENSION : ext;
    }

    /**
     * 是否为受支持的图片扩展名
     *
     * @param extension 扩展名（内部会先规范化，故传 {@code .JPG} 也能正确判断）
     */
    public static boolean isSupportedImage(String extension) {
        return IMAGE_EXTENSIONS.contains(normalizeExtension(extension));
    }

    /**
     * 由扩展名推断 MIME 类型，用于对象存储上传与多模态接口的 content-type
     */
    public static String contentTypeOf(String extension) {
        switch (normalizeExtension(extension)) {
            case "png":
                return "image/png";
            case "webp":
                return "image/webp";
            case "bmp":
                return "image/bmp";
            case "heic":
                return "image/heic";
            case "jpeg":
            case "jpg":
            default:
                return "image/jpeg";
        }
    }

    /**
     * 从「文件名或 objectKey」推断 MIME 类型
     */
    public static String contentTypeOfFile(String filenameOrKey) {
        return contentTypeOf(extensionOf(filenameOrKey));
    }

    /**
     * 返回受支持的图片扩展名集合（用于拼接错误提示文案，避免各服务各写一份）
     */
    public static Set<String> supportedImageExtensions() {
        return IMAGE_EXTENSIONS;
    }
}
