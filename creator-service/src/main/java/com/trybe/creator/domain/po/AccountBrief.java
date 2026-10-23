package com.trybe.creator.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * {@code app_user} 只读投影（个人中心「账号资料」段）
 *
 * <p><b>账号体系 v3</b>：创作者与普通用户共用 {@code app_user}，不再有 {@code web_user}。
 * {@code creator_profile.creator_id} 即 {@code app_user.id}。
 *
 * <p><b>只读约定</b>：creator-service <b>禁止对 app_user 做任何写操作</b>（写入归属 user-service）。
 * 手机号不在本表，由 {@code AccountBriefMapper} 关联 {@code user_identity} 查出，
 * 服务层脱敏（138****8000）后才进入 VO。
 *
 * @author TryBe Team
 */
@Data
@TableName("app_user")
public class AccountBrief implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 账号昵称（注册昵称，与 creator_profile.nickname 公开昵称相互独立） */
    private String nickname;

    /** 账号创建时间 */
    private LocalDateTime createTime;

    /** 最近登录时间 */
    private LocalDateTime lastLoginTime;

    /** 头像 OSS objectKey（统一个头像源；小程序与后台共用） */
    private String avatar;

    /** 个人简介（统一简介源） */
    private String bio;

    /** 资料更新时间（用作头像 URL 的缓存版本号） */
    private LocalDateTime updateTime;

    /** 当前绑定手机号（非 app_user 列，由 mapper 关联 user_identity 查出） */
    @TableField(exist = false)
    private String phone;
}
