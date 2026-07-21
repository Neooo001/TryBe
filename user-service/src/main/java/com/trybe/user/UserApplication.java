package com.trybe.user;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.trybe.user.mapper")
@SpringBootApplication(scanBasePackages = {"com.trybe.user", "com.trybe.common"})
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}