package com.trybe.user.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import javax.validation.constraints.NotNull;

@Data
//@ApiModel(description = "登录表单实体")
public class LoginFormDTO {
//    @ApiModelProperty(value = "登录凭证", required = true)
//    @NotNull(message = "登录凭证不能为空")
    private String code;
//    @NotNull(message = "手机号密文不能为空")
//    @ApiModelProperty(value = "手机号密文", required = true)
    private String phoneCode;
}
