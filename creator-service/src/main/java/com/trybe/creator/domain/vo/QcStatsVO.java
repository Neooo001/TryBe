package com.trybe.creator.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 模特图质检聚合（S1 看板「质检通过率」）
 *
 * <p>口径：当前创作者名下全部模特图（含草稿物品的），按
 * {@code creator_item_model_image.qc_status} 分组统计。
 * 注意此处的 PENDING 是模特图质检状态，与物品状态无关。
 */
@Data
@ApiModel(description = "模特图质检聚合")
public class QcStatsVO {

    @ApiModelProperty("模特图总数")
    private Long total;

    @ApiModelProperty("质检通过数")
    private Long pass;

    @ApiModelProperty("质检警告数")
    private Long warn;

    @ApiModelProperty("质检不通过数")
    private Long fail;

    @ApiModelProperty("待质检数")
    private Long pending;

    @ApiModelProperty("通过率百分比（保留 1 位小数），总数为 0 时为 0.0")
    private Double passRate;
}
