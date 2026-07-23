package com.trybe.user.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 用户资料 VO（编辑资料接口返回）
 */
@ApiModel(description = "用户资料")
@Data
public class UserProfileVO {

    @ApiModelProperty("用户ID")
    private Long userId;

    @ApiModelProperty("昵称")
    private String nickname;

    @ApiModelProperty("头像URL")
    private String avatar;

    @ApiModelProperty("个性签名")
    private String bio;

    @ApiModelProperty("性别：0未知 1男 2女")
    private Integer gender;

    @ApiModelProperty("手机号")
    private String phone;
}
