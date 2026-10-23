package com.trybe.common.auth;

/**
 * 短信验证码使用场景
 *
 * <p>用于区分发码入口，便于日志审计与后续差异化策略（例如不同场景不同限流阈值）。
 * 当前实现仅做语义标注，限流策略统一由 {@link SmsRateLimiter} 承担。
 *
 * <p><b>双账号域拆分后</b>：登录统一为「手机号+密码」，不再有短信验证码登录，
 * 故删除 LOGIN 场景。当前短信只服务于 wx 域的两件事：绑定手机号、设置密码。
 *
 * @author TryBe Team
 */
public enum SmsScene {

    /** 绑定/换绑手机号场景（小程序） */
    BIND("绑定手机号"),

    /** 设置/修改密码场景（两端共用） */
    SET_PASSWORD("设置密码"),

    /** Web 端短信验证码登录（账号体系 v3 新增，小程序不开放短信登录） */
    WEB_LOGIN("Web 短信登录"),

    /** Web 端手机号注册 */
    WEB_REGISTER("Web 手机号注册");

    private final String desc;

    SmsScene(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 解析场景字符串，非法或为空时回退到 {@link #BIND}。
     *
     * <p>避免前端未传 scene 时发码失败；scene 仅用于语义标注，不影响安全链路。
     *
     * @param name 场景名（可为 null / 空）
     * @return 合法场景，缺省 {@link #BIND}
     */
    public static SmsScene of(String name) {
        if (name == null || name.trim().isEmpty()) {
            return BIND;
        }
        for (SmsScene scene : values()) {
            if (scene.name().equalsIgnoreCase(name.trim())) {
                return scene;
            }
        }
        return BIND;
    }
}
