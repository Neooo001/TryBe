package com.trybe.user.domain.dto;

import lombok.Data;

/**
 * 用户资料更新请求
 */
@Data
public class UserProfileDTO {
    /** 用户昵称 */
    private String nickname;
    /** 个性签名 */
    private String bio;
    /** 性别（0未知 1男 2女） */
    private Integer gender;
}