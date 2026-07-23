package com.trybe.user.service;

/**
 * 密码管理服务接口
 *
 * @author TryBe Team
 */
public interface IPasswordService {

    /**
     * 设置密码（首次设置或修改均调用此方法）
     *
     * @param userId  当前登录用户ID
     * @param code    短信验证码
     * @param password 明文密码（由 Service 层加密后存储）
     */
    void setPassword(Long userId, String code, String password);
}