package com.trybe.creator.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 发布趋势（S1 看板「发布趋势」）
 *
 * <p>last7Days 恒 7 条、last30Days 恒 30 条，均为自然日升序且含今日；
 * 7 天数据由 30 天结果切片而来，不做二次查询。
 */
@Data
@ApiModel(description = "发布趋势")
public class PublishTrendVO {

    @ApiModelProperty("近 7 天（含今日，恒 7 条，日期升序）")
    private List<TrendPointVO> last7Days;

    @ApiModelProperty("近 30 天（含今日，恒 30 条，日期升序）")
    private List<TrendPointVO> last30Days;
}
