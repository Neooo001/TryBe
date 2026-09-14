package com.trybe.wardrobe.service;

import com.trybe.wardrobe.domain.dto.TaobaoProductDTO;

/**
 * 淘宝联盟对接服务
 */
public interface ITaobaoService {

    /**
     * 根据商品ID查询淘宝客商品详情
     *
     * @param numIid 商品ID
     * @return 商品详情，不存在返回 null
     */
    TaobaoProductDTO getItemInfo(Long numIid);

    /**
     * 通过淘宝联盟官方API解析淘口令/分享文案中的商品ID
     *
     * <p>调用 taobao.tbk.tpwd.parse 接口，从分享文案中解析出商品ID。
     * 优先从响应的 num_iid 字段直接获取，其次从 click_url / origin_url 中提取。</p>
     *
     * @param shareText 淘口令或分享文案
     * @return 商品ID（num_iid），解析失败返回 null
     */
    Long tpwdParse(String shareText);
}