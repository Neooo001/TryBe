package com.trybe.creator.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物品列表视图对象（I1 / R1 出参）
 */
@Data
@ApiModel(description = "物品列表信息")
public class ItemListVO {

    @ApiModelProperty("物品ID")
    private Long id;

    @ApiModelProperty("物品业务编号")
    private String itemNo;

    @ApiModelProperty("标题")
    private String title;

    @ApiModelProperty("一级分类")
    private String categoryCode;

    @ApiModelProperty("状态")
    private String status;

    @ApiModelProperty("价格（元）")
    private BigDecimal price;

    @ApiModelProperty("主图预签名访问 URL")
    private String imageUrl;

    @ApiModelProperty("最近一次驳回原因")
    private String rejectReason;

    @ApiModelProperty("创作者昵称（R1 审核列表展示）")
    private String creatorNickname;

    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;
}
