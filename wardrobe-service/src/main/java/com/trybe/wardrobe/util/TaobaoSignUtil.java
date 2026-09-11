package com.trybe.wardrobe.util;

import cn.hutool.crypto.digest.DigestUtil;

import java.util.Map;
import java.util.TreeMap;

/**
 * 淘宝开放平台 TOP 签名工具
 *
 * <p>签名算法：对所有请求参数按 key 升序排序，拼接为 keyvalue 串（不含 sign 本身），
 * 前后各补 appSecret，然后 MD5 并转大写。</p>
 */
public final class TaobaoSignUtil {

    private TaobaoSignUtil() {
    }

    /**
     * 生成 TOP 请求签名
     *
     * @param params    请求参数（不含 sign）
     * @param appSecret 应用密钥
     * @return 大写 MD5 签名
     */
    public static String sign(Map<String, String> params, String appSecret) {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder sb = new StringBuilder();
        sb.append(appSecret);
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            sb.append(entry.getKey()).append(entry.getValue());
        }
        sb.append(appSecret);
        return DigestUtil.md5Hex(sb.toString()).toUpperCase();
    }
}
