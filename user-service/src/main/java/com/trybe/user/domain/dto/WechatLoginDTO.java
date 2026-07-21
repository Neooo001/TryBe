package com.trybe.user.domain.dto;

import lombok.Data;

@Data
public class WechatLoginDTO {
    /**
     * 用户唯一标识
     */
    private String code;
    /**
     * 登录令牌
     */
    private String token;
}