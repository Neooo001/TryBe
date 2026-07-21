package com.trybe.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trybe.user.domain.po.User;
import com.trybe.user.domain.vo.UserLoginVO;
import com.trybe.user.domain.vo.UserProfileVO;

public interface IUserService extends IService<User> {

    /**
     * 微信小程序登录
     *
     * @param wxCode 微信登录凭证
     * @return 登录结果（含 token）
     */
    UserLoginVO loginByWechat(String wxCode);

    /**
     * 更新用户资料（头像 + 昵称 + 个性签名 + 性别）
     *
     * @param userId   当前用户ID
     * @param avatar   头像objectKey（由后端上传到OSS后返回）
     * @param nickname 用户昵称
     * @param bio      个性签名
     * @param gender   性别（0未知 1男 2女）
     * @return 更新后的用户资料 VO
     */
    UserProfileVO updateProfile(Long userId, String avatar, String nickname, String bio, Integer gender);

    /**
     * 获取用户资料（头像为预签名URL）
     *
     * @param userId 用户ID
     * @return 用户资料 VO
     */
    UserProfileVO getUserProfile(Long userId);
}