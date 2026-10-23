package com.trybe.user.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * Web 端手机号注册请求（{@code POST /web/auth/register}）
 *
 * <p>账号体系 v3：<b>手机号只能通过短信验证码绑定</b>，因此注册必须带 {@code code}。
 * 注册即登录，成功后返回双令牌。
 *
 * <p>{@code nickname} 可空：留空时服务端随机生成（需求：首次注册进入 Web 页随机生成昵称）；
 * 若手机号已存在（小程序先注册），则<b>沿用原昵称</b>，此处传值不生效。
 *
 * @author TryBe Team
 */
@Data
@ApiModel("Web 注册请求")
public class WebRegisterDTO {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @ApiModelProperty(value = "手机号", example = "13800138000", required = true)
    private String phone;

    @NotBlank(message = "短信验证码不能为空")
    @Pattern(regexp = "^\\d{4,8}$", message = "短信验证码格式不正确")
    @ApiModelProperty(value = "短信验证码", example = "123456", required = true)
    private String code;

    @Size(max = 32, message = "昵称不能超过32个字符")
    @ApiModelProperty(value = "昵称（可空，留空随机生成）")
    private String nickname;
}
