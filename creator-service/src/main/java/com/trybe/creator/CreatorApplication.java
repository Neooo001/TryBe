package com.trybe.creator;

import com.trybe.api.config.DefaultFeignConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 创作者后台服务启动类
 *
 * <p>职责：创作者物品 CRUD / 图片上传 / 模特图与质检 / 数据看板。
 * 与 user-service 的交互：共享 trybe 库只读 user 表 + Redis 角色缓存（不走 Feign 查 role）。
 */
@MapperScan("com.trybe.creator.mapper")
@EnableFeignClients(basePackages = "com.trybe.api.client", defaultConfiguration = DefaultFeignConfig.class)
@SpringBootApplication(scanBasePackages = {"com.trybe.creator", "com.trybe.common"})
public class CreatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(CreatorApplication.class, args);
    }
}
