package com.trybe.user.service;

import com.trybe.user.domain.vo.WechatSessionVO;

public interface WechatService {

    /**
     * 调用微信小程序登录接口，获取 session
     *
     * @param wxCode 微信登录凭证
     * @return 微信会话信息（openid + session_key）
     */
    WechatSessionVO getSession(String wxCode);
}
