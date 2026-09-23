package com.trybe.user.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 创作者端登录 / 注册返回 VO — 双 Token 体系
 *
 * <p>与小程序侧 {@code UserLoginVO} 完全解耦：创作者后台有自己独立的返回结构，
 * 不携带 openid / bio / gender 等小程序侧字段，也不提供已废弃的 token 字段。
 *
 * <p>双 Token 约定沿用项目既有体系：accessToken 为 JWT（默认 30min），
 * refreshToken 为随机字符串（滚动续期），前端凭 {@link #expiresIn} 决定是否预刷新。
 *
 * @author TryBe Team
 */
@Data
@ApiModel("创作者登录返回")
public class CreatorLoginVO {

    @ApiModelProperty("用户 ID")
    private Long userId;

    @ApiModelProperty("用户昵称")
    private String nickname;

    @ApiModelProperty("头像访问地址")
    private String avatar;

    @ApiModelProperty("注册手机号")
    private String phone;

    @ApiModelProperty("角色：CREATOR 创作者 / ADMIN 运营")
    private String role;

    // ===== 双 Token =====

    @ApiModelProperty("JWT AccessToken，有效期 30 分钟")
    private String accessToken;

    @ApiModelProperty("RefreshToken，仅用于换取新的 AccessToken")
    private String refreshToken;

    @ApiModelProperty("AccessToken 剩余有效时间（秒）")
    private long expiresIn;
}
