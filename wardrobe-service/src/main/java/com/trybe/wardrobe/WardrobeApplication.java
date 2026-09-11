package com.trybe.wardrobe;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@MapperScan("com.trybe.wardrobe.mapper")
@EnableFeignClients(basePackages = "com.trybe.api.client")
@SpringBootApplication(scanBasePackages = {"com.trybe.wardrobe", "com.trybe.common"})
public class WardrobeApplication {
    public static void main(String[] args) {
        SpringApplication.run(WardrobeApplication.class, args);
    }
}
