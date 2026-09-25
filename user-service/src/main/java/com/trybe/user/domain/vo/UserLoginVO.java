package com.trybe.user.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.trybe.user.enums.PhoneState;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户登录返回 VO — 双 Token 体系
 *
 * <p>accessToken 为 JWT（30min 有效期），refreshToken 为随机字符串（30天滚动续期）。
 * 前端应使用 {@link #expiresIn} 判断何时触发主动预刷新。
 *
 * @author TryBe Team
 */
@Data
public class UserLoginVO {
    private Long userId;
    private String nickname;
    private String avatar;
    private String phone;
    private String openid;
    private String bio;
    private Integer gender;

    /** 手机号归属状态：0从未绑定 1已绑定 2曾绑定被释放（小程序据此区分硬拦截/软提示） */
    private PhoneState phoneState;

    // ===== 双 Token =====
    /** JWT AccessToken（30min 有效） */
    private String accessToken;
    /** 随机字符串 RefreshToken（30天滚动续期，仅用于刷新 AccessToken） */
    private String refreshToken;
    /** AccessToken 剩余有效时间（秒） */
    private long expiresIn;

    /**
     * @deprecated 保留旧字段兼容，实际与 accessToken 相同。
     * 前端优先使用 accessToken，此字段仅用于平滑过渡。
     */
    @Deprecated
    private String token;

    /**
     * 兼容旧字段：设置 accessToken 时同步填充 token
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
        this.token = accessToken;
    }
}