package com.trybe.user.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

/**
 * Web 端资料更新请求（{@code PUT /web/users/profile}，需登录）
 *
 * <p>账号体系 v3 起，昵称 / 头像 / 简介是「人」的属性，统一存在 {@code app_user}，
 * 小程序、创作者后台、圈子作品卡片读的都是同一份 —— 改一次，全端生效。
 *
 * <p>字段语义统一为「null = 不修改；空串 = 清空（昵称除外）」，
 * 避免前端只改一项时把其它项误清空。
 *
 * @author TryBe Team
 */
@Data
@ApiModel("Web 资料更新请求")
public class WebProfileUpdateDTO {

    @Size(max = 32, message = "昵称不能超过32个字符")
    @ApiModelProperty("昵称（null=不修改；不能传空串）")
    private String nickname;

    @Size(max = 512, message = "简介不能超过512个字符")
    @ApiModelProperty("简介（null=不修改；空串=清空）")
    private String bio;

    @Size(max = 255, message = "头像路径过长")
    @ApiModelProperty("头像 OSS objectKey（null=不修改；空串=恢复默认头像）")
    private String avatarKey;

    @Min(value = 0, message = "性别取值为 0/1/2")
    @Max(value = 2, message = "性别取值为 0/1/2")
    @ApiModelProperty("性别：0未知 1男 2女（null=不修改）")
    private Integer gender;
}
