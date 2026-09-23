package com.trybe.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * 网关跨域（CORS）配置
 *
 * <h3>背景</h3>
 * <p>网关此前完全没有 CORS 配置，浏览器在调用跨域接口（例如
 * {@code POST /users/creator/auth/register}）前发出的 <b>OPTIONS 预检请求</b>
 * 拿不到 {@code Access-Control-Allow-Origin}，导致请求被浏览器直接拦下，服务端日志里甚至看不到请求进入。
 *
 * <h3>为什么使用 CorsWebFilter 而不是 spring.cloud.gateway.globalcors</h3>
 * <ol>
 *   <li>{@link CorsWebFilter} 是一个 WebFlux 的 {@code WebFilter}，它在
 *       {@code DispatcherHandler} 之前执行 —— 也就是在 <b>路由匹配</b> 和
 *       <b>AuthGlobalFilter（order=0）鉴权</b> 之前执行。
 *       这保证了 OPTIONS 预检一定能在鉴权之前被拦下并直接返回 200 + CORS 头。</li>
 *   <li>路由来自 Nacos 的 {@code gateway-routes.json}，每次动态刷新都会重建 RouteDefinition；
 *       而 globalcors 需要依赖 route definition 上的 {@code Cors} GatewayFilter，
 *       在路由尚未注册时（例如 Nacos 抖动、还没拉取到配置）会失效。放在 WebFilter 层则与路由生命周期解耦。</li>
 *   <li>本项目的 Nacos 上并不存在 {@code gateway-dev.yaml}，网关只拉取
 *       {@code shared-log.yaml} 与 {@code shared-redis.yaml}，因此不存在「Nacos 也已配 globalcors」的重复配置风险。</li>
 * </ol>
 *
 * <h3>重复响应头保护</h3>
 * <p>Spring 的 {@code DefaultCorsProcessor} 在处理前会检查响应里是否已经存在
 * {@code Access-Control-Allow-Origin}，若已存在则跳过，因此即便后续有人在 Nacos 的
 * {@code gateway-dev.yaml} 里又加了 {@code spring.cloud.gateway.globalcors}，
 * 也不会出现 multi-value 的 {@code Access-Control-Allow-Origin}。
 *
 * @author TryBe Team
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(CorsProperties.class)
@ConditionalOnProperty(prefix = "hm.cors", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CorsConfig {

    /**
     * 跨域配置生效的路径范围：全部网关路由。
     */
    private static final String CORS_PATH_PATTERN = "/**";

    /**
     * 注册跨域过滤器。
     *
     * <p>Spring 会通过 {@code FactoryAwareOrderSourceProvider} 读取 @Bean 工厂方法上的
     * {@link Order} 注解来决定 WebFilter 的执行顺序，这里设置为最高优先级；
     * 同时由于 WebFilter 链整体先于网关的 GlobalFilter 链执行，
     * 因此它必然早于 {@link com.trybe.gateway.filters.AuthGlobalFilter}（order=0）生效。
     *
     * @param corsProperties 跨域配置项，见 {@link CorsProperties}
     * @return 跨域过滤器
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsWebFilter corsWebFilter(CorsProperties corsProperties) {
        CorsConfiguration corsConfiguration = buildCorsConfiguration(corsProperties);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(CORS_PATH_PATTERN, corsConfiguration);

        log.info("[CORS] 网关跨域已启用 allowedOriginPatterns={} allowedMethods={} allowCredentials={}",
                corsProperties.getAllowedOrigins(),
                corsProperties.getAllowedMethods(),
                corsProperties.isAllowCredentials());

        return new CorsWebFilter(source);
    }

    /**
     * 依据配置项构建 {@link CorsConfiguration}。
     *
     * <p>关键点：{@code allowCredentials=true} 时必须使用 {@code setAllowedOriginPatterns}，
     * 不能使用 {@code setAllowedOrigins("*")}，否则运行时会抛出
     * {@code IllegalArgumentException: When allowCredentials is true, allowedOrigins cannot contain the special value "*"}.
     *
     * @param corsProperties 跨域配置项
     * @return 构造好的跨域配置对象
     */
    private CorsConfiguration buildCorsConfiguration(CorsProperties corsProperties) {
        CorsConfiguration corsConfiguration = new CorsConfiguration();

        // allowCredentials=true 时 allowedOrigins 禁止使用 "*"，走 patterns 匹配（支持端口通配）
        corsConfiguration.setAllowedOriginPatterns(corsProperties.getAllowedOrigins());
        corsConfiguration.setAllowedMethods(corsProperties.getAllowedMethods());
        corsConfiguration.setAllowedHeaders(corsProperties.getAllowedHeaders());
        corsConfiguration.setExposedHeaders(corsProperties.getExposedHeaders());
        corsConfiguration.setAllowCredentials(Boolean.valueOf(corsProperties.isAllowCredentials()));
        corsConfiguration.setMaxAge(corsProperties.getMaxAge());

        return corsConfiguration;
    }
}
