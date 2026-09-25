package com.trybe.user.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Web 域登录/注册返回（{@code /web/auth/**}）
 *
 * <p>与 wx 域 {@link UserLoginVO} 完全独立：Web 域没有 openid / phoneState / 角色概念，
 * 返回的令牌 realm=web，只能访问 {@code /web/**} 与 {@code /creator/**}。
 *
 * @author TryBe Team
 */
@Data
@ApiModel("Web 登录返回")
public class WebLoginVO {

    @ApiModelProperty("用户 ID")
    private Long userId;

    @ApiModelProperty("昵称")
    private String nickname;

    @ApiModelProperty("JWT AccessToken（realm=web）")
    private String accessToken;

    @ApiModelProperty("RefreshToken，仅用于换取新的 AccessToken")
    private String refreshToken;

    @ApiModelProperty("AccessToken 剩余有效时间（秒）")
    private long expiresIn;
}
