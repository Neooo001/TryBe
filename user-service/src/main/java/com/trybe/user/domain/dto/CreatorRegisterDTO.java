package com.trybe.user.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 创作者注册请求
 *
 * <p>创作者后台专用，与小程序侧登录/绑定 DTO 完全独立：注册成功即写入
 * {@code role = CREATOR}，无需再走入驻申请与人工审核。
 *
 * @author TryBe Team
 */
@Data
@ApiModel("创作者注册请求")
public class CreatorRegisterDTO {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @ApiModelProperty(value = "手机号", example = "13800138000", required = true)
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度须为 6~32 位")
    @ApiModelProperty(value = "密码（6~32 位）", required = true)
    private String password;

    @Size(max = 32, message = "昵称最长 32 个字符")
    @ApiModelProperty(value = "昵称，留空则使用默认昵称", example = "我的工作室")
    private String nickname;

    @NotBlank(message = "短信验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "短信验证码为 6 位数字")
    @ApiModelProperty(value = "短信验证码", example = "123456", required = true)
    private String code;

    @NotBlank(message = "图形验证码标识不能为空")
    @ApiModelProperty(value = "图形验证码标识（captchaId）", required = true)
    private String captchaId;

    @NotBlank(message = "图形验证码不能为空")
    @Size(min = 4, max = 4, message = "图形验证码为 4 位")
    @ApiModelProperty(value = "图形验证码", example = "8k2f", required = true)
    private String captchaCode;
}
