package com.trybe.user.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserLoginVO {
    private Long userId;
    private String nickname;
    private String avatar;
    private String phone;
    private String openid;
    private String bio;
    private Integer gender;
    private String token;
}