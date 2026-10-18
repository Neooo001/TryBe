package com.trybe.content;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * C 端内容分发服务启动类
 *
 * 职责：把创作者发布的内容，以「浏览者视角」分发给小程序（主页瀑布流 / 圈子信息流）。
 *
 * <p><b>为什么只扫两个包</b>：{@code com.trybe.common} 里还装着 captchaStore / 短信 / 人脸识别 /
 * OSS 等一堆 Bean，它们依赖 Redis、阿里云 SDK，而 C 端分发服务一样都用不上
 * （它不连库、不发短信、不碰 OSS，数据全靠 Feign 向 creator-service 取）。
 * 扫整个 common 会直接把 {@code CaptchaStore} 拉起来 →
 * {@code NoClassDefFoundError: StringRedisTemplate} 启动失败。
 * 这里只扫自己的包 + 全局异常处理所在的 advice 包。
 */
@EnableFeignClients(basePackages = "com.trybe.api.client", defaultConfiguration = com.trybe.api.config.DefaultFeignConfig.class)
@SpringBootApplication(scanBasePackages = {"com.trybe.content", "com.trybe.common.advice"})
public class ContentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentApplication.class, args);
    }
}