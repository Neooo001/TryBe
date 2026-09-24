package com.trybe.user.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * 绑定手机号请求
 *
 * <p>统一账号体系 v2：绑定/换绑 <b>不含图形验证码</b> —— 短信验证码即强凭据
 * （证明持有 SIM），发码阶段已有「图形码 + 三级限流 + 60s 间隔」三重防护。
 *
 * @author TryBe Team
 */
@Data
@ApiModel("绑定手机号请求")
public class BindPhoneDTO {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @ApiModelProperty(value = "手机号", example = "13800138000", required = true)
    private String phone;

    @NotBlank(message = "验证码不能为空")
    @ApiModelProperty(value = "短信验证码", example = "123456", required = true)
    private String code;
}
