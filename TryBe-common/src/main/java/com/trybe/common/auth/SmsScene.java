package com.trybe.common.auth;

/**
 * 短信验证码使用场景
 *
 * <p>用于区分发码入口，便于日志审计与后续差异化策略（例如不同场景不同限流阈值）。
 * 当前实现仅做语义标注，限流策略统一由 {@link SmsRateLimiter} 承担。
 *
 * @author TryBe Team
 */
public enum SmsScene {

    /** 登录场景：短信验证码登录（既有账号或自动注册） */
    LOGIN("登录"),

    /** 绑定/换绑手机号场景 */
    BIND("绑定手机号"),

    /** 注册场景（创作者后台注册等） */
    REGISTER("注册");

    private final String desc;

    SmsScene(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 解析场景字符串，非法或为空时回退到 {@link #LOGIN}。
     *
     * <p>避免前端未传 scene 时发码失败；scene 仅用于语义标注，不影响安全链路。
     *
     * @param name 场景名（可为 null / 空）
     * @return 合法场景，缺省 {@link #LOGIN}
     */
    public static SmsScene of(String name) {
        if (name == null || name.trim().isEmpty()) {
            return LOGIN;
        }
        for (SmsScene scene : values()) {
            if (scene.name().equalsIgnoreCase(name.trim())) {
                return scene;
            }
        }
        return LOGIN;
    }
}
