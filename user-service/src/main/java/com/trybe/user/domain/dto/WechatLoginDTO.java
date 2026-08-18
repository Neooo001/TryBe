package com.trybe.user.domain.dto;

import lombok.Data;

@Data
public class WechatLoginDTO {
    /**
     * 微信登录临时code（wx.login 返回）
     */
    private String code;
}