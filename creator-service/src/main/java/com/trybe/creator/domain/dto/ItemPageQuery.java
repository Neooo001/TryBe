package com.trybe.creator.domain.dto;

import com.trybe.common.domain.PageQuery;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 作品分页查询条件（I1 我的作品列表）
 *
 * <p><b>不放「一级分类」筛选</b>：{@code ai_category_code} 是 AI 解析的平台侧数据，
 * 创作者看不到。如果留着这个筛选项，创作者就能用二分试探反推出 AI 给出的分类，
 * 等于把「不可见」绕过去了。同理，keyword 与任何筛选条件都**不得**落在 {@code ai_} 列上。
 *
 * <p>⚠️ 分页参数名沿用 {@link PageQuery} 的 {@code pageNo} / {@code pageSize}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "作品分页查询条件")
public class ItemPageQuery extends PageQuery {

    @ApiModelProperty("作品状态过滤：DRAFT/ON_SHELF/OFF_SHELF（可空）")
    private String status;

    @ApiModelProperty("关键词：模糊匹配标题 / 描述 / 分区 / 标签 / 款式 / 材质（可空，均为创作者自己的字段）")
    private String keyword;
}
