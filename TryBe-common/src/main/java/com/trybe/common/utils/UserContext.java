package com.trybe.common.utils;

import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.UnauthorizedException;

/**
 * 当前请求的用户上下文（ThreadLocal）
 *
 * <p>由 {@code UserInfoInterceotor} 在请求进入时写入，请求结束时清除。
 * 除 userId 外还承载 <b>账号域（realm）</b>——双账号域拆分后，同一个 userId
 * 在 wx 域与 web 域分别代表不同的人，服务内做域断言时必须带上 realm。
 */
public class UserContext {
    private static final ThreadLocal<Long> tl = new ThreadLocal<>();

    /** 账号域 ThreadLocal（wx / web） */
    private static final ThreadLocal<String> realmTl = new ThreadLocal<>();

    /**
     * 保存当前登录用户信息到ThreadLocal
     * @param userId 用户id
     */
    public static void setUser(Long userId) {
        tl.set(userId);
    }

    /**
     * 获取当前登录用户信息
     * @return 用户id
     */
    public static Long getUser() {
        return tl.get();
    }

    /**
     * 获取当前登录用户，未登录时直接抛出未授权异常
     *
     * <p>Controller 层原先各自定义了一个私有 requireLogin()，四处实现完全一致。
     * 收敛到这里可以保证「未登录」的响应码与提示文案在全项目绝对统一，
     * 也避免新增 Controller 时漏写登录校验。
     *
     * @return 当前登录用户id（保证非 null）
     * @throws UnauthorizedException 未登录时抛出
     */
    public static Long requireUser() {
        Long userId = tl.get();
        if (userId == null) {
            throw new UnauthorizedException(ErrorCodeEnum.NOT_LOGIN, "请先登录");
        }
        return userId;
    }

    /**
     * 保存当前请求的账号域标识
     *
     * @param realm {@code wx} / {@code web}
     */
    public static void setRealm(String realm) {
        realmTl.set(realm);
    }

    /**
     * 获取当前请求的账号域标识
     *
     * @return realm，网关未透传时为 null
     */
    public static String getRealm() {
        return realmTl.get();
    }

    /**
     * 移除当前登录用户信息（<b>userId 与 realm 一并清除</b>，
     * 避免 Tomcat 线程复用导致的跨请求串域）
     */
    public static void removeUser(){
        tl.remove();
        realmTl.remove();
    }
}
