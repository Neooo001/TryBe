package com.trybe.user.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 创作者注册发送短信验证码请求
 *
 * <p>与小程序侧 {@link SendCodeDTO}（/users/phone/send-code）区分开：
 * 本 DTO 面向**免鉴权**的注册发码接口，因此强制要求图形验证码，
 * 并在服务端叠加手机号 / IP / 全局三级限流。
 *
 * @author TryBe Team
 */
@Data
@ApiModel("创作者注册发送验证码请求")
public class CreatorSendCodeDTO {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @ApiModelProperty(value = "手机号", example = "13800138000", required = true)
    private String phone;

    @NotBlank(message = "图形验证码标识不能为空")
    @ApiModelProperty(value = "图形验证码标识（captchaId）", required = true)
    private String captchaId;

    @NotBlank(message = "图形验证码不能为空")
    @Size(min = 4, max = 4, message = "图形验证码为 4 位")
    @ApiModelProperty(value = "图形验证码", example = "8k2f", required = true)
    private String captchaCode;
}
