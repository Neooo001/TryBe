package com.trybe.creator.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 发布趋势点（S1 看板「发布趋势」）
 *
 * <p>按自然日分组的物品创建数，缺失日期补 0，日期格式 yyyy-MM-dd。
 */
@Data
@ApiModel(description = "发布趋势点")
public class TrendPointVO {

    @ApiModelProperty("自然日，yyyy-MM-dd")
    private String date;

    @ApiModelProperty("当日创建的物品数")
    private Long count;
}
