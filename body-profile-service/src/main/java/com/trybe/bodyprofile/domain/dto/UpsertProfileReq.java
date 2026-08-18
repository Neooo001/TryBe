package com.trybe.bodyprofile.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 更新/创建身形档案身型数据请求（upsert，不存在则创建）
 */
@Data
@ApiModel(description = "保存身形档案请求")
public class UpsertProfileReq {

    @ApiModelProperty("档案名称")
    private String name;

    @ApiModelProperty("身高(cm)")
    private BigDecimal height;

    @ApiModelProperty("体重(kg)")
    private BigDecimal weight;

    @ApiModelProperty("胸围(cm)")
    private BigDecimal bust;

    @ApiModelProperty("腰围(cm)")
    private BigDecimal waist;

    @ApiModelProperty("臀围(cm)")
    private BigDecimal hip;

    @ApiModelProperty("肩宽(cm)")
    private BigDecimal shoulder;

    @ApiModelProperty("臂长(cm)")
    private BigDecimal arm;

    @ApiModelProperty("腿长(cm)")
    private BigDecimal leg;

    @ApiModelProperty("体型标签：STRAIGHT-直筒型, PEAR-梨型, APPLE-苹果型, HOURGLASS-沙漏型, INVERTED_TRIANGLE-倒三角型")
    private String bodyType;

    @ApiModelProperty("肤色：冷白皮/暖白皮/自然色/小麦色/古铜色")
    private String skinTone;

    @ApiModelProperty("期望修饰部位（多选）")
    private List<String> modifyParts;

    @ApiModelProperty("备注")
    private String remark;
}
