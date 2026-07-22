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
                .excludePathPatterns("/users/login", "/auth/refresh-token");
    }
}
