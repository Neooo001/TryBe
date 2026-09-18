package com.trybe.wardrobe.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * 淘宝商品图处理工具
 *
 * <p>解决三件事：
 * <ol>
 *   <li>URL 归一化：补协议（// 开头、http 升级 https），并去掉阿里 CDN 拼接的缩略图后缀
 *       （如 {@code xxx.jpg_430x430q90.jpg}、{@code xxx.jpg_.webp}），拿到原始大图；</li>
 *   <li>带移动端 UA 下载，避免被当作异常流量拒绝；</li>
 *   <li>大小与文件头校验，防止把错误页/占位图当成商品图存进 OSS。</li>
 * </ol>
 */
@Slf4j
public final class TaobaoImageUtil {

    private static final String MOBILE_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1";

    private static final int TIMEOUT_MS = 10_000;
    private static final long MAX_BYTES = 5 * 1024 * 1024;

    /**
     * 阿里 CDN 拼接的缩略图后缀，例如 xxx.jpg_430x430q90.jpg（尺寸+质量）
     * 注意：只吃「尺寸型」后缀，不能一刀切从第一个下划线截断 —— 商品图文件名本身常带下划线
     * （如 O1CN01abc_!!123.jpg），误截会丢掉扩展名。
     */
    private static final Pattern SIZE_SUFFIX = Pattern.compile(
            "_\\d+x\\d+[^/]*?\\.(jpg|jpeg|png|webp|gif)$", Pattern.CASE_INSENSITIVE);

    /** 格式转换型后缀，例如 xxx.jpg_.webp */
    private static final Pattern FORMAT_SUFFIX = Pattern.compile(
            "_\\.(jpg|jpeg|png|webp|gif)$", Pattern.CASE_INSENSITIVE);

    private TaobaoImageUtil() {
    }

    /**
     * 归一化图片 URL：补 https 协议，去掉缩略图后缀
     *
     * @param url 原始 URL，可能为 null 或以 // 开头
     * @return 归一化后的 URL，输入为空时返回 null
     */
    public static String normalize(String url) {
        if (StrUtil.isBlank(url)) {
            return null;
        }
        String result = url.trim();
        if (result.startsWith("//")) {
            result = "https:" + result;
        } else if (result.startsWith("http://")) {
            result = "https" + result.substring(4);
        }
        // 可能存在多重后缀（如 xxx.jpg_430x430q90.jpg_.webp），最多剥两轮
        for (int i = 0; i < 2; i++) {
            String before = result;
            result = SIZE_SUFFIX.matcher(result).replaceAll("");
            result = FORMAT_SUFFIX.matcher(result).replaceAll("");
            if (result.equals(before)) {
                break;
            }
        }
        return result;
    }

    /**
     * 下载商品图
     *
     * <p>先按归一化后的原图地址下载，失败时回退用原始 URL 再试一次。</p>
     *
     * @param rawUrl 商品图 URL
     * @return 图片字节；失败返回 null
     */
    public static byte[] download(String rawUrl) {
        String url = normalize(rawUrl);
        if (url == null) {
            return null;
        }
        byte[] bytes = doGet(url);
        if (bytes == null && !url.equals(rawUrl)) {
            bytes = doGet(rawUrl);
        }
        return bytes;
    }

    private static byte[] doGet(String url) {
        try (HttpResponse response = HttpRequest.get(url)
                .header("User-Agent", MOBILE_UA)
                .timeout(TIMEOUT_MS)
                .execute()) {

            int status = response.getStatus();
            if (status != 200) {
                log.warn("[TaobaoImage] 下载图片失败 status={} url={}", status, url);
                return null;
            }

            byte[] bytes = response.bodyBytes();
            if (bytes == null || bytes.length == 0) {
                log.warn("[TaobaoImage] 图片内容为空 url={}", url);
                return null;
            }
            if (bytes.length > MAX_BYTES) {
                log.warn("[TaobaoImage] 图片超过大小限制 size={} url={}", bytes.length, url);
                return null;
            }
            if (!looksLikeImage(bytes)) {
                log.warn("[TaobaoImage] 内容不是合法图片 url={}", url);
                return null;
            }
            return bytes;
        } catch (Exception e) {
            log.warn("[TaobaoImage] 下载图片异常 url={}", url, e);
            return null;
        }
    }

    private static boolean looksLikeImage(byte[] bytes) {
        if (bytes.length < 12) {
            return false;
        }
        int b0 = bytes[0] & 0xFF;
        int b1 = bytes[1] & 0xFF;
        // JPEG: FF D8 FF
        if (b0 == 0xFF && b1 == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return true;
        }
        // PNG: 89 50 4E 47
        if (b0 == 0x89 && b1 == 0x50 && (bytes[2] & 0xFF) == 0x4E && (bytes[3] & 0xFF) == 0x47) {
            return true;
        }
        // GIF: 47 49 46
        if (b0 == 0x47 && b1 == 0x49 && (bytes[2] & 0xFF) == 0x46) {
            return true;
        }
        // WEBP: RIFF....WEBP
        boolean riff = b0 == 0x52 && b1 == 0x49 && (bytes[2] & 0xFF) == 0x46 && (bytes[3] & 0xFF) == 0x46;
        boolean webp = (bytes[8] & 0xFF) == 0x57 && (bytes[9] & 0xFF) == 0x45
                && (bytes[10] & 0xFF) == 0x42 && (bytes[11] & 0xFF) == 0x50;
        return riff && webp;
    }
}
