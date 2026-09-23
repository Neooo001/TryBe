package com.trybe.creator.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 类目分布统计行（S1 看板「类目分布」）
 *
 * <p>由后端按 {@code GarmentCategoryL1} 枚举顺序恒返回 8 条，无数据补 0，
 * 保证前端条形图顺序稳定。
 */
@Data
@ApiModel(description = "类目分布统计行")
public class CategoryStatVO {

    @ApiModelProperty("一级分类 code，如 TOP")
    private String categoryCode;

    @ApiModelProperty("一级分类中文名，如上装")
    private String categoryLabel;

    @ApiModelProperty("该类目下的物品数量")
    private Long count;

    @ApiModelProperty("占比百分比（保留 1 位小数），总数为 0 时为 0.0")
    private Double ratio;
}
