package com.trybe.user.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 入驻资质图预签名请求（U2）
 */
@Data
@ApiModel(description = "入驻资质图预签名请求")
public class LicensePresignReq {

    @ApiModelProperty(value = "图片扩展名，如 jpg/png", required = true)
    @NotBlank(message = "扩展名不能为空")
    private String ext;
}
