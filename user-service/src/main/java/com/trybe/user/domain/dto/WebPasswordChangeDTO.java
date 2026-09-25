package com.trybe.user.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * Web 域修改密码请求（{@code POST /web/auth/password/change}，需登录）
 *
 * <p>Web 域无短信通道（注册/登录均不用短信），故改密凭<b>旧密码</b>验证，
 * 成功后该用户在本域的全部 RefreshToken 失效（强制重登）。
 *
 * @author TryBe Team
 */
@Data
@ApiModel("Web 修改密码请求")
public class WebPasswordChangeDTO {

    @NotBlank(message = "原密码不能为空")
    @ApiModelProperty(value = "原密码", required = true)
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9]{6,20}$", message = "密码需为6-20位字母和数字组合")
    @ApiModelProperty(value = "新密码", required = true)
    private String newPassword;
}
