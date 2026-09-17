package com.trybe.common.utils;

import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.UnauthorizedException;

public class UserContext {
    private static final ThreadLocal<Long> tl = new ThreadLocal<>();

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
     * 移除当前登录用户信息
     */
    public static void removeUser(){
        tl.remove();
    }
}
