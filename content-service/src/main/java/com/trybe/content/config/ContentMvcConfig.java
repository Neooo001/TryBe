package com.trybe.content.config;

import com.trybe.common.interceptors.UserInfoInterceotor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * content-service 的 MVC 配置 —— 只为注册 {@link UserInfoInterceotor}
 *
 * <p><b>为什么不能像别的服务那样直接用 common 的 {@code MvcConfig}</b>：
 * 本服务的组件扫描范围被刻意收窄成 {@code com.trybe.content} + {@code com.trybe.common.advice}
 * （全扫 common 会拉起 {@code CaptchaStore} 等依赖 Redis 的 Bean，启动直接失败），
 * 所以 {@code com.trybe.common.config.MvcConfig} 根本没有被加载 ——
 * 拦截器不生效，{@code UserContext} 恒为 null。
 *
 * <p>这在只做「公开作品浏览」时没问题（谁看都一样），但「我的作品」必须知道
 * <b>当前是谁</b>：content-service 要把 userId 经 Feign 透传给 creator-service。
 * 所以这里只把那个拦截器单独 new 出来注册，不引入 common 里任何别的 Bean。
 *
 * <p>拦截器本身<b>不阻断请求</b>，只负责把网关透传的 {@code user-info} /
 * {@code user-realm} 头写进 ThreadLocal，因此不需要 excludePathPatterns。
 *
 * @author TryBe Team
 */
@Configuration
public class ContentMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserInfoInterceotor()).addPathPatterns("/**");
    }
}
