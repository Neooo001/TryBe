package com.trybe.material.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 上传成功回调请求
 */
@Data
@ApiModel(description = "上传成功回调请求")
public class UploadCallbackReq {

    @ApiModelProperty(value = "素材ID", required = true)
    @NotNull(message = "素材ID不能为空")
    private Long materialId;
}
