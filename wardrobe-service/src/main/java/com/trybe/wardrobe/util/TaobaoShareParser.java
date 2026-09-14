package com.trybe.wardrobe.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 淘宝分享文案解析工具
 *
 * <p>从形如「【淘宝】https://e.tb.cn/h.8IQi1tW?tk=0IUuT7nKZ8W ...」的分享文案中解析出商品ID。
 * 解析策略：先取链接，尝试从 URL 直接提取，失败则跟随短链重定向，从最终落地 URL 提取，
 * 最后兜底用长数字串匹配。所有策略均失败时返回 null，由调用方决定是否走 API 兜底。</p>
 */
@Slf4j
public final class TaobaoShareParser {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s，。`」』】}）'\"\\]]+");
    private static final Pattern ID_PARAM_PATTERN = Pattern.compile("[?&](?:id|itemId|item_id)=([0-9]{6,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM_PATH_PATTERN = Pattern.compile("/item/([0-9]{6,})");
    private static final Pattern META_REFRESH_PATTERN = Pattern.compile(
            "http-equiv=['\"]?refresh['\"]?[^>]*content=['\"]?\\d+;\\s*url=['\"]?([^'\"\\s>]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern JS_LOCATION_PATTERN = Pattern.compile(
            "(?:window\\.)?location\\.(?:href|replace)\\s*[=\\(]\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);

    private static final int MAX_REDIRECT = 5;

    private TaobaoShareParser() {
    }

    /**
     * 从分享文案快速解析商品ID（纯本地策略，无网络开销或仅跟随重定向）
     *
     * @param shareText 分享文案
     * @return 商品ID（num_iid），解析失败返回 null
     */
    public static String parseNumIid(String shareText) {
        if (StrUtil.isBlank(shareText)) {
            return null;
        }

        String url = extractUrl(shareText);
        if (url == null) {
            // 纯淘口令（文案里没有任何链接）本地无解，交由上层决定是否走 API 兜底
            log.debug("[TaobaoShareParser] 文案中未包含链接，本地无法解析");
            return null;
        }

        String numIid = extractNumIidFromUrl(url);
        if (numIid != null) {
            return numIid;
        }

        String finalUrl = followRedirects(url);
        return extractNumIidFromUrl(finalUrl);
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

    /**
     * 把 Location 头解析为绝对地址（淘宝偶发返回相对路径）
     */
    private static String resolveAbsolute(String baseUrl, String location) {
        if (StrUtil.isBlank(location)) {
            return null;
        }
        if (location.startsWith("http")) {
            return location;
        }
        try {
            return new java.net.URI(baseUrl).resolve(location).toString();
        } catch (Exception e) {
            log.warn("[TaobaoShareParser] 相对地址解析失败 base={} location={}", baseUrl, location);
            return null;
        }
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
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1")
                .setFollowRedirects(false)
                .timeout(8000)
                .execute()) {

            String location = resolveAbsolute(url, response.header("Location"));
            if (location != null) {
                return location;
            }

            String body = response.body();
            if (StrUtil.isNotBlank(body)) {
                String fromBody = extractRedirectFromBody(body);
                if (fromBody != null) {
                    return fromBody;
                }
            }

            return null;
        } catch (Exception e) {
            log.warn("[TaobaoShareParser] 跟随重定向失败 url={}", url, e);
            return null;
        }
    }

    private static String extractRedirectFromBody(String body) {
        Matcher metaMatcher = META_REFRESH_PATTERN.matcher(body);
        if (metaMatcher.find()) {
            String url = metaMatcher.group(1);
            if (url.startsWith("http")) {
                return url;
            }
        }

        Matcher jsMatcher = JS_LOCATION_PATTERN.matcher(body);
        if (jsMatcher.find()) {
            String url = jsMatcher.group(1);
            if (url.startsWith("http")) {
                return url;
            }
        }

        return null;
    }
}