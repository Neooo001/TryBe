package com.trybe.bodyprofile;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@MapperScan("com.trybe.bodyprofile.mapper")
@EnableFeignClients(basePackages = "com.trybe.api.client")
@SpringBootApplication(scanBasePackages = {"com.trybe.bodyprofile", "com.trybe.common"})
public class BodyProfileApplication {
    public static void main(String[] args) {
        SpringApplication.run(BodyProfileApplication.class, args);
    }
}
