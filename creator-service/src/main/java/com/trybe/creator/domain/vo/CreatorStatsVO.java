package com.trybe.creator.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 创作者数据看板视图对象（S1 出参）
 */
@Data
@ApiModel(description = "创作者数据看板")
public class CreatorStatsVO {

    @ApiModelProperty("物品总数")
    private Long totalItems;

    @ApiModelProperty("草稿数")
    private Long draft;

    @ApiModelProperty("待审数")
    private Long pending;

    @ApiModelProperty("已通过数（未上架）")
    private Long approved;

    @ApiModelProperty("已驳回数")
    private Long rejected;

    @ApiModelProperty("已上架数")
    private Long onShelf;

    @ApiModelProperty("已下架数")
    private Long offShelf;

    @ApiModelProperty("今日新增数")
    private Long todayNew;
}
