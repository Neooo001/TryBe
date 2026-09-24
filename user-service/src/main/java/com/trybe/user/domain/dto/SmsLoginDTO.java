package com.trybe.user.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * 短信验证码登录请求（/users/auth/login/sms）
 *
 * <p><b>刻意不含图形验证码</b>：短信验证码即强凭据（证明持有 SIM），
 * 且发码侧已有「图形码 + 三级限流 + 60s 间隔」三重防护；
 * 避免图形码 TTL 300s 到期导致莫名登录失败（主理人裁决 ⑤）。
 *
 * @author TryBe Team
 */
@Data
@ApiModel("短信验证码登录请求")
public class SmsLoginDTO {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @ApiModelProperty(value = "手机号", example = "13800138000", required = true)
    private String phone;

    /**
     * 短信验证码：**只校验纯数字，不锁定长度**。
     * <p>位数由阿里云 dypnsapi 下发（{@code CodeLength} 允许 4~8 位），此处按 4~8 放宽，
     * 避免后续调整位数时后端误拦合法验证码；前端亦不做长度校验。
     */
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{4,8}$", message = "短信验证码格式不正确")
    @ApiModelProperty(value = "短信验证码（4~8 位数字）", example = "1234", required = true)
    private String code;
}
