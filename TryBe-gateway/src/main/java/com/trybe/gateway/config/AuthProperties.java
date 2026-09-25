package com.trybe.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// 网关的认证白名单 ，不需要认证的接口
@Data
@Component
@ConfigurationProperties(prefix = "hm.auth")
public class AuthProperties {
    private List<String> includePaths;
    private List<String> excludePaths;

    /**
     * 账号域 → 该域可访问的路径模式（Ant 语法）
     *
     * <pre>
     * hm:
     *   auth:
     *     realm-paths:
     *       wx:  [/users/**, /wardrobe/**, /body-profile/**, /material/**]
     *       web: [/web/**, /creator/**]
     * </pre>
     *
     * <p>双账号域拆分后，userId 只在域内唯一，必须按路径校验 token 中的 realm，
     * 否则 A 域 token 可越权访问 B 域接口（等于访问另一个人的账号）。
     */
    private Map<String, List<String>> realmPaths;
}
