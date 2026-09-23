package com.trybe.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 网关跨域（CORS）配置项
 *
 * <p>配置前缀 {@code hm.cors}，与本项目现有的 {@code hm.jwt} / {@code hm.auth} 保持一致的命名风格。
 * 所有属性都带有开发环境默认值，因此即使不在任何配置文件中显式书写，网关也会放行前端本地调试域名。
 *
 * <p>如需新增来源（例如局域网真机调试），可以在
 * {@code TryBe-gateway/src/main/resources/application.yml} 中追加：
 *
 * <pre>{@code
 * hm:
 *   cors:
 *     enabled: true
 *     allowed-origins:
 *       - http://localhost:5173
 *       - http://127.0.0.1:5173
 *       - http://localhost:*
 *       - http://127.0.0.1:*
 *   }</pre>
 *
 * <p>注意：一旦配置了 {@code allow-credentials: true}（默认值），
 * {@code allowed-origins} 会被当作 <b>origin pattern</b> 使用，
 * 绝不能使用裸的 {@code *}，必须写成带 scheme 的完整来源，端口可用 {@code *} 通配。
 *
 * @author TryBe Team
 */
@Data
@ConfigurationProperties(prefix = "hm.cors")
public class CorsProperties {

    /**
     * 是否启用网关跨域过滤；关闭后所有的 CORS 响应头将由网关下游服务自行负责。
     */
    private boolean enabled = true;

    /**
     * 允许的来源 patterns。
     *
     * <p>默认覆盖前端 Vite 开发服务器的所有常见形态：
     * <ul>
     *   <li>{@code http://localhost:5173} / {@code http://127.0.0.1:5173} —— Vite 默认开发端口</li>
     *   <li>{@code http://localhost:*} / {@code http://127.0.0.1:*} —— vite --port 指定任意端口</li>
     *   <li>{@code https://localhost:*} / {@code https://127.0.0.1:*} —— 本地启 HTTPS 的场景</li>
     * </ul>
     */
    private List<String> allowedOrigins = new ArrayList<>(Arrays.asList(
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://localhost:*",
            "http://127.0.0.1:*",
            "https://localhost:*",
            "https://127.0.0.1:*"
    ));

    /**
     * 允许的 HTTP 方法，OPTIONS 必须保留，否则浏览器预检无法通过。
     */
    private List<String> allowedMethods = new ArrayList<>(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"
    ));

    /**
     * 允许携带的请求头，业务侧使用了 Authorization，这里统一放行全部。
     */
    private List<String> allowedHeaders = new ArrayList<>(Arrays.asList("*"));

    /**
     * 允许前端 JavaScript 读取的响应头。
     */
    private List<String> exposedHeaders = new ArrayList<>(Arrays.asList(
            "Authorization",
            "Content-Disposition",
            "Cache-Control"
    ));

    /**
     * 是否允许携带凭证（Cookie / Authorization 自定义头），双 Token 体系下必须为 true。
     */
    private boolean allowCredentials = true;

    /**
     * 预检结果缓存时长，减少 OPTIONS 请求数量。
     */
    private Duration maxAge = Duration.ofHours(1);
}
