package com.trybe.common.config;

import com.trybe.common.interceptors.UserInfoInterceotor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置 — 注册 UserInfoInterceotor
 *
 * <p>Gateway 鉴权通过后将 userId 写入 user-info header，
 * UserInfoInterceotor 读取并写入 UserContext(ThreadLocal)，
 * Controller 层通过 {@code UserContext.getUser()} 获取当前用户。
 *
 * @author TryBe Team
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserInfoInterceotor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/users/login",
                        "/auth/refresh-token",
                        "/hi",
                        // wx 小程序免鉴权：图形码、发短信码、密码登录
                        // 注意：/users/auth/phone/bind 与 /users/auth/phone 必须登录，不得加入
                        "/users/auth/captcha",
                        "/users/auth/sms/code",
                        "/users/auth/login/password",
                        // Web 创作者后台免鉴权：图形码、发短信码、注册、密码登录、短信登录
                        // 注意：/web/auth/sms/code/set-password、/web/auth/password/set、
                        //      /web/auth/password/change 必须登录，不得加入
                        "/web/auth/captcha",
                        "/web/auth/sms/code",
                        "/web/auth/register",
                        "/web/auth/login",
                        "/web/auth/login/sms");
    }
}
