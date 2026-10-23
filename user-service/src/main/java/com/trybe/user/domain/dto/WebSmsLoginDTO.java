package com.trybe.user.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * Web 端短信验证码登录请求（{@code POST /web/auth/login/sms}）
 *
 * <p>只认已注册手机号：未注册返回「该手机号未注册，请先注册」，避免「登录即静默注册」
 * 导致用户以为登录了别人的账号。
 *
 * @author TryBe Team
 */
@Data
@ApiModel("Web 短信登录请求")
public class WebSmsLoginDTO {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @ApiModelProperty(value = "手机号", example = "13800138000", required = true)
    private String phone;

    @NotBlank(message = "短信验证码不能为空")
    @Pattern(regexp = "^\\d{4,8}$", message = "短信验证码格式不正确")
    @ApiModelProperty(value = "短信验证码", example = "123456", required = true)
    private String code;
}
