package com.trybe.creator.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 运营全局数据看板视图对象（S2 出参）
 */
@Data
@ApiModel(description = "运营全局数据看板")
public class AdminStatsVO {

    @ApiModelProperty("创作者总数（user 表 role=CREATOR）")
    private Long totalCreators;

    @ApiModelProperty("物品总数")
    private Long totalItems;

    @ApiModelProperty("待审物品数")
    private Long pendingItems;

    @ApiModelProperty("今日新增物品数")
    private Long todayNewItems;

    @ApiModelProperty("今日新增入驻申请数")
    private Long todayNewApplications;
}
