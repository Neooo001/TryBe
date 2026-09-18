package com.trybe.user.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 入驻申请审核请求（U6，运营）
 */
@Data
@ApiModel(description = "入驻申请审核请求")
public class ReviewApplicationReq {

    @ApiModelProperty(value = "是否通过：true通过/false驳回", required = true)
    @NotNull(message = "审核结论不能为空")
    private Boolean approve;

    @ApiModelProperty("驳回原因（驳回时必填）")
    @Size(max = 255, message = "驳回原因不能超过255个字符")
    private String reason;
}
