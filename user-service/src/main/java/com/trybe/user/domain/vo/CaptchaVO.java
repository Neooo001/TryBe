package com.trybe.user.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图形验证码返回
 *
 * <p>{@code imageBase64} 为 PNG 的纯 Base64（不含 {@code data:image/png;base64,} 前缀），
 * 前端自行拼接前缀后展示。
 *
 * @author TryBe Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("图形验证码")
public class CaptchaVO {

    @ApiModelProperty("验证码标识，发短信与注册时回传")
    private String captchaId;

    @ApiModelProperty("PNG Base64（不含 data URI 前缀）")
    private String imageBase64;

    @ApiModelProperty("有效期（秒）")
    private long expireSeconds;
}
