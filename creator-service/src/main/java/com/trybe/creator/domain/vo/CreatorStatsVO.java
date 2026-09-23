package com.trybe.creator.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 创作者数据看板视图对象（S1 出参）
 *
 * <p>四类指标：KPI（总数/草稿/上架/下架/今日新增）、类目分布、模特图质检、发布趋势。
 * 一次请求返回，前端看板只发一次调用。
 */
@Data
@ApiModel(description = "创作者数据看板")
public class CreatorStatsVO {

    @ApiModelProperty("物品总数")
    private Long totalItems;

    @ApiModelProperty("草稿数")
    private Long draft;

    @ApiModelProperty("已上架数")
    private Long onShelf;

    @ApiModelProperty("已下架数")
    private Long offShelf;

    @ApiModelProperty("今日新增数")
    private Long todayNew;

    @ApiModelProperty("类目分布（恒 8 条，无数据补 0）")
    private List<CategoryStatVO> categoryStats;

    @ApiModelProperty("模特图质检聚合")
    private QcStatsVO qcStats;

    @ApiModelProperty("发布趋势（近 7 天 / 近 30 天）")
    private PublishTrendVO publishTrend;
}
