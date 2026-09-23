package com.trybe.creator.domain.dto;

import com.trybe.common.domain.PageQuery;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 物品分页查询条件（I1 我的物品列表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "物品分页查询条件")
public class ItemPageQuery extends PageQuery {

    @ApiModelProperty("物品状态过滤：DRAFT/ON_SHELF/OFF_SHELF（可空）")
    private String status;

    @ApiModelProperty("一级分类过滤（可空）")
    private String categoryCode;

    @ApiModelProperty("标题关键字（模糊匹配，可空）")
    private String keyword;
}
