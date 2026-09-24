package com.trybe.user.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 密码登录请求（/users/auth/login/password）
 *
 * <p>图形验证码前置（P0-5）：密码登录必须带图形码，防撞库；
 * 同时用「统一文案」防账号枚举。
 *
 * @author TryBe Team
 */
@Data
@ApiModel("密码登录请求")
public class PasswordLoginDTO {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @ApiModelProperty(value = "手机号", example = "13800138000", required = true)
    private String phone;

    @NotBlank(message = "密码不能为空")
    @ApiModelProperty(value = "密码", required = true)
    private String password;

    @NotBlank(message = "图形验证码标识不能为空")
    @ApiModelProperty(value = "图形验证码标识（captchaId）", required = true)
    private String captchaId;

    @NotBlank(message = "图形验证码不能为空")
    @Size(min = 4, max = 4, message = "图形验证码为 4 位")
    @ApiModelProperty(value = "图形验证码", example = "8k2f", required = true)
    private String captchaCode;
}
