package com.trybe.user.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Web 端（创作者后台）登录/注册返回 —— {@code /web/auth/**}
 *
 * <p>账号体系 v3 后，Web 端与小程序端<b>共用同一个账号</b>（{@code app_user}），
 * 令牌 {@code realm=web} 只表示「这是 Web 端的会话」，不再代表另一个人。
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

    @ApiModelProperty("手机号（脱敏 138****8000）")
    private String phone;

    @ApiModelProperty("是否已设置密码（false 时前端引导设置密码）")
    private boolean hasPassword;

    @ApiModelProperty("是否本次新建的账号")
    private boolean newUser;

    @ApiModelProperty("首次注册端：WX 小程序 / WEB 创作者后台")
    private String registerSource;

    @ApiModelProperty("JWT AccessToken（realm=web）")
    private String accessToken;

    @ApiModelProperty("RefreshToken，仅用于换取新的 AccessToken")
    private String refreshToken;

    @ApiModelProperty("AccessToken 剩余有效时间（秒）")
    private long expiresIn;
}
