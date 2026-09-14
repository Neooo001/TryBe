package com.trybe.wardrobe.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BizIllegalException;
import com.trybe.wardrobe.config.TaobaoProperties;
import com.trybe.wardrobe.domain.dto.TaobaoProductDTO;
import com.trybe.wardrobe.service.ITaobaoService;
import com.trybe.wardrobe.util.TaobaoSignUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 淘宝联盟对接服务实现
 *
 * <p>调用 taobao.tbk.item.info.get（淘宝客商品详情查询·简版）获取商品信息与图片。
 * 说明：该接口为「淘宝客【公用】物料信息查询」权限包下的免费接口，不需要 adzone_id、不需要用户授权，
 * 返回字段固定（不支持 fields 参数），传 fields 会被网关判为 invalid-parameter。</p>
 *
 * <p>商品ID的获取走本地解析（TaobaoShareParser：正则 + 跟随短链 302），不依赖任何解析类接口；
 * taobao.tbk.tpwd.parse 属「淘口令解析&转链」权限包（不默认开放），仅在开关打开时作为兜底调用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaobaoServiceImpl implements ITaobaoService {

    private static final String METHOD_ITEM_INFO_GET = "taobao.tbk.item.info.get";
    private static final String METHOD_TPWD_PARSE = "taobao.tbk.tpwd.parse";

    private static final Pattern URL_ID_PATTERN = Pattern.compile("[?&](?:id|itemId|item_id)=([0-9]{6,})", Pattern.CASE_INSENSITIVE);

    private final TaobaoProperties taobaoProperties;

    @Override
    public TaobaoProductDTO getItemInfo(Long numIid) {
        Map<String, Object> params = buildParams(numIid);
        Map<String, String> stringParams = new HashMap<>();
        params.forEach((k, v) -> stringParams.put(k, String.valueOf(v)));
        String sign = TaobaoSignUtil.sign(stringParams, taobaoProperties.getAppSecret());
        params.put("sign", sign);

        String responseBody;
        try {
            responseBody = HttpUtil.post(taobaoProperties.getEndpoint(), params);
        } catch (Exception e) {
            log.error("[Taobao] 调用淘宝接口异常 numIid={}", numIid, e);
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "淘宝接口调用失败，请稍后再试");
        }

        return parseResponse(responseBody);
    }

    @Override
    public Long tpwdParse(String shareText) {
        // 该接口属「淘口令解析&转链」权限包，官方不默认开放，未开通时调用必然报权限错误且白耗日调用量
        if (!taobaoProperties.isTpwdParseEnabled()) {
            log.debug("[Taobao] 淘口令解析开关未开启，跳过 API 兜底");
            return null;
        }
        if (StrUtil.isBlank(shareText)) {
            return null;
        }

        Map<String, Object> params = buildTpwdParseParams(shareText);
        Map<String, String> stringParams = new HashMap<>();
        params.forEach((k, v) -> stringParams.put(k, String.valueOf(v)));
        String sign = TaobaoSignUtil.sign(stringParams, taobaoProperties.getAppSecret());
        params.put("sign", sign);

        String responseBody;
        try {
            responseBody = HttpUtil.post(taobaoProperties.getEndpoint(), params);
        } catch (Exception e) {
            log.error("[Taobao] 调用淘口令解析接口异常 shareText={}", shareText, e);
            return null;
        }

        return parseTpwdResponse(responseBody);
    }

    private Map<String, Object> buildTpwdParseParams(String shareText) {
        Map<String, Object> params = new HashMap<>();
        params.put("method", METHOD_TPWD_PARSE);
        params.put("app_key", taobaoProperties.getAppKey());
        params.put("timestamp", cn.hutool.core.date.DateUtil.now());
        params.put("format", "json");
        params.put("v", "2.0");
        params.put("sign_method", "md5");
        params.put("password_content", shareText);
        params.put("adzone_id", String.valueOf(taobaoProperties.getAdzoneId()));
        return params;
    }

    private Long parseTpwdResponse(String responseBody) {
        if (StrUtil.isBlank(responseBody)) {
            log.warn("[Taobao] 淘口令解析接口返回为空");
            return null;
        }

        JSONObject root;
        try {
            root = JSONUtil.parseObj(responseBody);
        } catch (Exception e) {
            log.error("[Taobao] 淘口令解析接口返回解析失败 body={}", responseBody, e);
            return null;
        }

        if (root.containsKey("error_response")) {
            JSONObject error = root.getJSONObject("error_response");
            log.warn("[Taobao] 淘口令解析接口返回错误 code={} msg={} subMsg={}",
                    error.getStr("code"), error.getStr("msg"), error.getStr("sub_msg"));
            return null;
        }

        JSONObject result = extractTpwdResult(root);
        if (result == null) {
            log.warn("[Taobao] 淘口令解析接口未返回结果 body={}", responseBody);
            return null;
        }

        Long numIid = result.getLong("num_iid");
        if (numIid != null) {
            log.info("[Taobao] 淘口令解析成功 num_iid={}", numIid);
            return numIid;
        }

        Long fromUrl = extractNumIidFromUrls(result);
        if (fromUrl != null) {
            log.info("[Taobao] 淘口令解析成功(从URL提取) num_iid={}", fromUrl);
            return fromUrl;
        }

        log.warn("[Taobao] 淘口令解析接口未返回商品ID body={}", responseBody);
        return null;
    }

    private JSONObject extractTpwdResult(JSONObject root) {
        Object response = root.get("tbk_tpwd_parse_response");
        if (!(response instanceof JSONObject)) {
            return null;
        }
        return ((JSONObject) response).getJSONObject("result");
    }

    private Long extractNumIidFromUrls(JSONObject result) {
        String[] urlFields = {"click_url", "origin_url", "item_url"};
        for (String field : urlFields) {
            String url = result.getStr(field);
            if (StrUtil.isNotBlank(url)) {
                Matcher matcher = URL_ID_PATTERN.matcher(url);
                if (matcher.find()) {
                    try {
                        return Long.valueOf(matcher.group(1));
                    } catch (NumberFormatException e) {
                        log.warn("[Taobao] 从URL提取商品ID格式错误 url={}", url);
                    }
                }
            }
        }
        return null;
    }

    private Map<String, Object> buildParams(Long numIid) {
        Map<String, Object> params = new HashMap<>();
        params.put("method", METHOD_ITEM_INFO_GET);
        params.put("app_key", taobaoProperties.getAppKey());
        params.put("timestamp", cn.hutool.core.date.DateUtil.now());
        params.put("format", "json");
        params.put("v", "2.0");
        params.put("sign_method", "md5");
        params.put("num_iids", String.valueOf(numIid));
        // platform：1-PC，2-无线。小程序场景统一用无线，item_url 返回 m 站链接，与移动端一致
        params.put("platform", "2");
        return params;
    }

    private TaobaoProductDTO parseResponse(String responseBody) {
        if (StrUtil.isBlank(responseBody)) {
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "淘宝接口返回为空");
        }

        JSONObject root;
        try {
            root = JSONUtil.parseObj(responseBody);
        } catch (Exception e) {
            log.error("[Taobao] 淘宝接口返回解析失败 body={}", responseBody, e);
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "淘宝接口返回解析失败");
        }

        // 错误响应
        if (root.containsKey("error_response")) {
            JSONObject error = root.getJSONObject("error_response");
            String subMsg = error.getStr("sub_msg");
            String msg = error.getStr("msg");
            log.warn("[Taobao] 淘宝接口返回错误 code={} msg={} subMsg={}",
                    error.getStr("code"), msg, subMsg);
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR,
                    StrUtil.isNotBlank(subMsg) ? subMsg : msg);
        }

        JSONObject item = firstItem(root);
        if (item == null) {
            log.warn("[Taobao] 淘宝接口未返回商品信息 body={}", responseBody);
            return null;
        }

        TaobaoProductDTO dto = new TaobaoProductDTO();
        dto.setNumIid(item.getLong("num_iid"));
        dto.setTitle(item.getStr("title"));
        dto.setPictUrl(item.getStr("pict_url"));
        dto.setSmallImages(parseSmallImages(item.getJSONObject("small_images")));
        dto.setReservePrice(item.getStr("reserve_price"));
        dto.setZkFinalPrice(item.getStr("zk_final_price"));
        dto.setItemUrl(item.getStr("item_url"));
        dto.setSellerId(item.getStr("seller_id"));
        dto.setShopTitle(item.getStr("shop_title"));
        dto.setVolume(item.getStr("volume"));
        dto.setProvcity(item.getStr("provcity"));
        // 官方返回一级类目字段为 cat_name；部分版本/场景可能带 cat_leaf_name，做兼容
        String category = item.getStr("cat_name");
        if (StrUtil.isBlank(category)) {
            category = item.getStr("cat_leaf_name");
        }
        dto.setCategory(category);
        dto.setRawJson(responseBody);
        return dto;
    }

    private JSONObject firstItem(JSONObject root) {
        Object response = root.get("tbk_item_info_get_response");
        if (!(response instanceof JSONObject)) {
            return null;
        }
        JSONObject resp = (JSONObject) response;
        Object results = resp.get("results");
        if (!(results instanceof JSONObject)) {
            return null;
        }
        Object array = ((JSONObject) results).get("n_tbk_item");
        if (array instanceof JSONArray) {
            JSONArray items = (JSONArray) array;
            return items.isEmpty() ? null : items.getJSONObject(0);
        }
        if (array instanceof JSONObject) {
            return (JSONObject) array;
        }
        return null;
    }

    private List<String> parseSmallImages(JSONObject smallImages) {
        if (smallImages == null) {
            return new ArrayList<>();
        }
        Object strings = smallImages.get("string");
        if (strings instanceof JSONArray) {
            List<String> result = new ArrayList<>();
            for (Object o : (JSONArray) strings) {
                if (o != null) {
                    result.add(String.valueOf(o));
                }
            }
            return result;
        }
        if (strings instanceof String) {
            List<String> result = new ArrayList<>();
            result.add((String) strings);
            return result;
        }
        return new ArrayList<>();
    }
}