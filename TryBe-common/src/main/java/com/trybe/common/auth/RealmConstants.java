package com.trybe.common.auth;

/**
 * 账号域常量（双账号域拆分后引入）
 *
 * <p>TryBe 拆分为两个完全独立的账号域，同一 user_id 在不同域中代表不同的人：
 * <ul>
 *   <li>{@link #WX}：小程序域，账号表 {@code wx_user}，标识为微信 openid（手机号可选凭据）</li>
 *   <li>{@link #WEB}：Web 创作者后台域，账号表 {@code web_user}，标识为手机号</li>
 * </ul>
 *
 * <p>域标识写入 JWT 的 {@link #CLAIM_REALM} claim，并由网关按路径校验，
 * 防止 A 域 token 访问 B 域接口（跨域 = 越权访问他人数据）。
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
