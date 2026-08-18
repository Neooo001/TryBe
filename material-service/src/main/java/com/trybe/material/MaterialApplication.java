package com.trybe.material;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.trybe.material.mapper")
@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.trybe.material", "com.trybe.common"})
public class MaterialApplication {
    public static void main(String[] args) {
        SpringApplication.run(MaterialApplication.class, args);
    }
}
