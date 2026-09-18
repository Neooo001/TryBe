package com.trybe.creator.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 审核请求（R3：通过/驳回）
 */
@Data
@ApiModel(description = "物品审核请求")
public class ReviewReq {

    @ApiModelProperty(value = "是否通过：true通过/false驳回", required = true)
    @NotNull(message = "审核结论不能为空")
    private Boolean approve;

    @ApiModelProperty("驳回原因（驳回时必填）")
    @Size(max = 255, message = "驳回原因不能超过255个字符")
    private String reason;
}
