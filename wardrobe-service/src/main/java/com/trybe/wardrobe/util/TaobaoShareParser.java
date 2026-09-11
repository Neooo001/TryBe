package com.trybe.wardrobe.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 淘宝分享文案解析工具
 *
 * <p>从形如「【淘宝】https://e.tb.cn/h.8IQi1tW?tk=0IUuT7nKZ8W ...」的分享文案中解析出商品ID。
 * 解析策略：先取链接，尝试从 URL 直接提取，失败则跟随短链重定向，从最终落地 URL 提取，
 * 最后兜底用长数字串匹配。</p>
 */
@Slf4j
public final class TaobaoShareParser {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s，。]+");
    private static final Pattern ID_PARAM_PATTERN = Pattern.compile("[?&](?:id|itemId|item_id)=([0-9]{6,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM_PATH_PATTERN = Pattern.compile("/item/([0-9]{6,})");
    private static final Pattern LONG_DIGIT_PATTERN = Pattern.compile("([0-9]{11,})");

    private static final int MAX_REDIRECT = 5;

    private TaobaoShareParser() {
    }

    /**
     * 从分享文案解析商品ID
     *
     * @param shareText 分享文案
     * @return 商品ID（num_iid）
     */
    public static String parseNumIid(String shareText) {
        if (StrUtil.isBlank(shareText)) {
            throw new BadRequestException(ErrorCodeEnum.PARAM_EMPTY, "分享文案不能为空");
        }

        String url = extractUrl(shareText);
        if (url == null) {
            // 无链接，尝试直接匹配长数字
            String numIid = extractLongDigit(shareText);
            if (numIid != null) {
                return numIid;
            }
            throw new BadRequestException(ErrorCodeEnum.PARAM_FORMAT_ERR, "未在分享文案中识别到淘宝链接");
        }

        String numIid = extractNumIidFromUrl(url);
        if (numIid != null) {
            return numIid;
        }

        String finalUrl = followRedirects(url);
        numIid = extractNumIidFromUrl(finalUrl);
        if (numIid != null) {
            return numIid;
        }

        numIid = extractLongDigit(shareText);
        if (numIid != null) {
            return numIid;
        }

        log.warn("[TaobaoShareParser] 无法解析商品ID shareText={} finalUrl={}", shareText, finalUrl);
        throw new BadRequestException(ErrorCodeEnum.PARAM_FORMAT_ERR, "无法从分享链接中解析商品ID");
    }

    private static String extractUrl(String text) {
        Matcher matcher = URL_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private static String extractNumIidFromUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return null;
        }
        Matcher idMatcher = ID_PARAM_PATTERN.matcher(url);
        if (idMatcher.find()) {
            return idMatcher.group(1);
        }
        Matcher pathMatcher = ITEM_PATH_PATTERN.matcher(url);
        if (pathMatcher.find()) {
            return pathMatcher.group(1);
        }
        return null;
    }

    private static String extractLongDigit(String text) {
        Matcher matcher = LONG_DIGIT_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String followRedirects(String url) {
        String current = url;
        for (int i = 0; i < MAX_REDIRECT; i++) {
            String location = resolveLocation(current);
            if (location == null) {
                return current;
            }
            current = location;
        }
        return current;
    }

    private static String resolveLocation(String url) {
        try (HttpResponse response = HttpRequest.get(url)
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X)")
                .setFollowRedirects(false)
                .timeout(8000)
                .execute()) {
            String location = response.header("Location");
            if (StrUtil.isBlank(location)) {
                return null;
            }
            // 相对路径补全（短链跳转一般返回绝对地址）
            if (location.startsWith("http")) {
                return location;
            }
            return null;
        } catch (Exception e) {
            log.warn("[TaobaoShareParser] 跟随重定向失败 url={}", url, e);
            return null;
        }
    }
}
