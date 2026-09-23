package com.trybe.user.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Pattern;

/**
 * 图形验证码请求
 *
 * <p>phone 选填：传入时验证码与该手机号绑定，防止「拿 A 的图形码给 B 发短信」。
 *
 * @author TryBe Team
 */
@Data
@ApiModel("图形验证码请求")
public class CaptchaReqDTO {

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @ApiModelProperty(value = "手机号（选填，传入后验证码与该号码绑定）", example = "13800138000")
    private String phone;
}
