package com.trybe.user.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 登录凭据类型
 *
 * <p>「凭据与主体分离」的核心枚举：一个 user 可持有 N 条凭据。
 * <ul>
 *   <li>{@link #PHONE}：手机号凭据，{@code app_id} 恒为空串，唯一键退化为 {@code (PHONE,'',手机号)}</li>
 *   <li>{@link #WECHAT_OPENID}：微信 openid 凭据，必须带小程序 {@code app_id}（openid 是「用户×appid」维度）</li>
 * </ul>
 *
 * @author TryBe Team
 */
@Getter
public enum IdentityType {

    /** 手机号 */
    PHONE("PHONE", "手机号"),

    /** 微信 openid */
    WECHAT_OPENID("WECHAT_OPENID", "微信openid");

    /** 落库值（对应 user_identity.identity_type） */
    @EnumValue
    private final String code;

    private final String desc;

    IdentityType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
