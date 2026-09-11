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

/**
 * 淘宝联盟对接服务实现
 *
 * <p>调用 taobao.tbk.item.info.get（淘宝客商品详情查询·简版）获取商品信息。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaobaoServiceImpl implements ITaobaoService {

    private static final String METHOD_ITEM_INFO_GET = "taobao.tbk.item.info.get";
    private static final String FIELDS = "num_iid,title,pict_url,small_images,reserve_price,zk_final_price,"
            + "item_url,seller_id,shop_title,volume,provcity,cat_leaf_name";

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

    private Map<String, Object> buildParams(Long numIid) {
        Map<String, Object> params = new HashMap<>();
        params.put("method", METHOD_ITEM_INFO_GET);
        params.put("app_key", taobaoProperties.getAppKey());
        params.put("timestamp", cn.hutool.core.date.DateUtil.now());
        params.put("format", "json");
        params.put("v", "2.0");
        params.put("sign_method", "md5");
        params.put("num_iids", String.valueOf(numIid));
        params.put("fields", FIELDS);
        params.put("platform", "1");
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
        dto.setCategory(item.getStr("cat_leaf_name"));
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
