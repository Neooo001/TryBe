package com.trybe.user.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * 设置密码请求
 *
 * @author TryBe Team
 */
@Data
@ApiModel("设置密码请求")
public class SetPasswordDTO {

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9]{6,20}$", message = "密码需为6-20位字母和数字组合")
    @ApiModelProperty(value = "密码", required = true)
    private String password;

    @NotBlank(message = "验证码不能为空")
    @ApiModelProperty(value = "短信验证码", example = "123456", required = true)
    private String code;
}