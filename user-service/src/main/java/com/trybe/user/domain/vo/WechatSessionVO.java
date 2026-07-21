package com.trybe.user.domain.vo;

import lombok.Data;

@Data
public class WechatSessionVO {
    /**
     * 用户唯一openid
     */
    private String openid;
    /**
     * 会话密钥
     */
    private String session_key;
    /**
     * 错误码 0为成功
     */
    private Integer errcode;
    /**
     * 错误信息
     */
    private String errmsg;
}