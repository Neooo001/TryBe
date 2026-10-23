package com.trybe.user.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * Web 端首次设置密码请求（{@code POST /web/auth/password/set}，需登录）
 *
 * <p>短信验证码发到该账号<b>当前已绑定</b>的手机号（不接受请求体传号），
 * 保证「验证码发给谁」与「给谁改密码」是同一个号。
 *
 * @author TryBe Team
 */
@Data
@ApiModel("Web 设置密码请求")
public class WebSetPasswordDTO {

    @NotBlank(message = "短信验证码不能为空")
    @Pattern(regexp = "^\\d{4,8}$", message = "短信验证码格式不正确")
    @ApiModelProperty(value = "短信验证码", example = "123456", required = true)
    private String code;

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9]{6,20}$", message = "密码需为6-20位字母或数字")
    @ApiModelProperty(value = "新密码（6-20位字母或数字）", required = true)
    private String password;
}
