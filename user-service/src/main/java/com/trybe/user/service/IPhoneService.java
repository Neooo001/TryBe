package com.trybe.user.service;

/**
 * 手机号管理服务接口
 *
 * @author TryBe Team
 */
public interface IPhoneService {

    /**
     * 绑定手机号
     *
     * @param userId 当前登录用户ID
     * @param phone  待绑定的手机号
     */
    void bind(Long userId, String phone);

    /**
     * 检查手机号是否已被其他用户绑定
     *
     * @param phone 手机号
     * @return true=已被占用
     */
    boolean isPhoneBound(String phone);
}
