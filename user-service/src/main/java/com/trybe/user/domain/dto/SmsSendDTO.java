package com.trybe.user.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 发送短信验证码请求（/users/auth/sms/code）
 *
 * <p>免鉴权接口，必须带图形验证码；服务端叠加「图形码 + 手机号/IP/全局三级限流 + 60s 间隔」三重防护。
 * {@code scene} 选填，仅用于语义标注。
 *
 * @author TryBe Team
 */
@Data
@ApiModel("发送短信验证码请求")
public class SmsSendDTO {

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

    @ApiModelProperty(value = "使用场景：LOGIN / BIND / REGISTER，选填，缺省 LOGIN", example = "BIND")
    private String scene;
}
