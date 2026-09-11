package com.trybe.wardrobe.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 淘宝客商品详情解析结果（item.info.get）
 */
@Data
public class TaobaoProductDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 淘宝商品ID */
    private Long numIid;

    /** 商品标题 */
    private String title;

    /** 商品主图URL */
    private String pictUrl;

    /** 商品小图URL列表 */
    private List<String> smallImages;

    /** 原价 */
    private String reservePrice;

    /** 折后价/券后价 */
    private String zkFinalPrice;

    /** 商品落地页URL */
    private String itemUrl;

    /** 卖家ID */
    private String sellerId;

    /** 店铺名称 */
    private String shopTitle;

    /** 30天销量 */
    private String volume;

    /** 发货地 */
    private String provcity;

    /** 商品类目 */
    private String category;

    /** 淘宝API原始返回JSON */
    private String rawJson;
}
