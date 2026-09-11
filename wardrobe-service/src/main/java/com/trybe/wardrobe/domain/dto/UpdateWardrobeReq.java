package com.trybe.wardrobe.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 衣柜条目编辑请求
 */
@Data
@ApiModel(description = "衣柜条目编辑请求")
public class UpdateWardrobeReq {

    @ApiModelProperty("标题")
    private String title;

    @ApiModelProperty("备注")
    private String remark;
}
