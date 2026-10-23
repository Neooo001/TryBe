package com.trybe.common.auth;

/**
 * 账号域常量（双账号域拆分后引入）
 *
 * <p><b>账号体系 v3 起，realm 不再是「账号域」，而是「端 / 会话」</b>：
 * 两端共用同一张 {@code app_user}、同一个 user_id，手机号是唯一身份锚点。
 * realm 只剩一个作用 —— 防止 A 端的令牌打 B 端接口（跨端越权）。
 * <ul>
 *   <li>{@link #WX}：小程序端会话（微信 openid 为凭据，手机号必绑）</li>
 *   <li>{@link #WEB}：创作者后台会话（手机号 + 短信码 / 密码为凭据）</li>
 * </ul>
 *
 * <p>域标识写入 JWT 的 {@link #CLAIM_REALM} claim，并由网关按路径校验。
 *
 * <p><b>为何放 common</b>：user-service（签发）与 TryBe-gateway（校验）是两份独立代码，
 * 必须引用同一个常量，否则 claim 名漂移会导致全站 401。
 *
 * @author TryBe Team
 */
public final class RealmConstants {

    /** 小程序域 */
    public static final String WX = "wx";

    /** Web 创作者后台域 */
    public static final String WEB = "web";

    /**
     * 任意域（跨域共享路径的占位 realm）
     *
     * <p>用于 {@code /auth/**} 这类「两个域都要用、但语义由令牌自身决定」的端点：
     * 登出注销的是「当前令牌所属域」的会话，刷新也是按令牌里的 realm 重签，
     * 因此不做域匹配，只要令牌合法即可。
     *
     * <p>配置在 {@code hm.auth.realm-paths} 下作为一个 key 使用，例如 {@code any: [/auth/**]}。
     */
    public static final String ANY = "any";

    /** JWT 中承载域标识的 claim 名 */
    public static final String CLAIM_REALM = "realm";

    /** 网关透传给下游服务的域标识请求头 */
    public static final String HEADER_USER_REALM = "user-realm";

    private RealmConstants() {
    }
}
