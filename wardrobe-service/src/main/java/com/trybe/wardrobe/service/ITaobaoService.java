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
}
