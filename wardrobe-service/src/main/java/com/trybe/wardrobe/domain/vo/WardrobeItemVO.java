package com.trybe.wardrobe.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 衣柜条目VO
 */
@Data
@ApiModel(description = "衣柜条目")
public class WardrobeItemVO {

    @ApiModelProperty("衣柜条目ID")
    private Long wardrobeId;

    @ApiModelProperty("来源：TAOBAO_IMPORT-淘宝导入, MANUAL-手动添加, CUSTOM_OUTFIT-自定义穿搭")
    private String source;

    @ApiModelProperty("商品ID（仅淘宝导入）")
    private Long productId;

    @ApiModelProperty("淘宝商品ID（仅淘宝导入）")
    private Long numIid;

    @ApiModelProperty("标题")
    private String title;

    @ApiModelProperty("备注")
    private String remark;

    @ApiModelProperty("主图临时访问URL")
    private String imageUrl;

    @ApiModelProperty("详情图临时访问URL列表")
    private List<String> detailImageUrls;

    @ApiModelProperty("原价")
    private String reservePrice;

    @ApiModelProperty("折后价/券后价")
    private String zkFinalPrice;

    @ApiModelProperty("商品落地页URL")
    private String itemUrl;

    @ApiModelProperty("店铺名称")
    private String shopTitle;

    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;
}