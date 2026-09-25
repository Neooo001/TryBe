package com.trybe.common.interceptors;

import cn.hutool.core.util.StrUtil;
import com.trybe.common.auth.RealmConstants;
import com.trybe.common.utils.UserContext;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 用户上下文拦截器 — 读取网关透传的 {@code user-info} 与 {@code user-realm}
 *
 * <p>双账号域拆分后，userId 只在「域内」唯一：wx 域的 1 号与 web 域的 1 号是两个人。
 * 因此除 userId 外必须同时还原 realm，供服务内做域断言。
 */
public class UserInfoInterceotor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String userInfo = request.getHeader("user-info");

        if (StrUtil.isNotBlank(userInfo)){
            UserContext.setUser(Long.valueOf(userInfo));
        }

        String realm = request.getHeader(RealmConstants.HEADER_USER_REALM);
        if (StrUtil.isNotBlank(realm)) {
            UserContext.setRealm(realm);
        }

        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserContext.removeUser();
    }
}
