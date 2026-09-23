package com.trybe.user.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * 创作者登录请求
 *
 * <p>与小程序侧的 {@code PhoneLoginDTO} 同源自 {@code user} 表，但作为独立的
 * 创作者端契约存在：后续创作者端扩展（图形验证码、微信扫码等）只改本 DTO，
 * 不影响小程序侧登录。
 *
 * @author TryBe Team
 */
@Data
@ApiModel("创作者登录请求")
public class CreatorLoginDTO {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @ApiModelProperty(value = "手机号", example = "13800138000", required = true)
    private String phone;

    @NotBlank(message = "密码不能为空")
    @ApiModelProperty(value = "密码", required = true)
    private String password;
}
