package com.trybe.user.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trybe.user.enums.PhoneState;
import com.trybe.user.enums.UserStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户表（统一账号体系 v2：只放「人」的属性）
 * </p>
 *
 * <p>凭据与主体分离后，本表不再承载登录凭据：
 * <ul>
 *   <li>手机号 → {@code user_identity(identity_type='PHONE')}</li>
 *   <li>openid → {@code user_identity(identity_type='WECHAT_OPENID', app_id=...)}</li>
 *   <li>密码   → {@code user_password(user_id)}</li>
 * </ul>
 * 本表仅保留：昵称、头像、简介、性别、状态、角色、unionid（预留）、phone_state。
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 头像OSS存储地址
     */
    private String avatar;

    /**
     * 使用状态（0冻结 1正常）
     */
    private UserStatus status;

    /**
     * 角色：USER普通用户/CREATOR创作者/ADMIN运营
     * <p>用 VARCHAR+String 存储（不建枚举），规避枚举类型处理器配置风险
     */
    private String role;

    /**
     * 微信开放平台 unionid（预留字段，个人主体拿不到，注册开放平台后可启用）
     */
    private String unionid;

    /**
     * 手机号归属状态：0从未绑定 1已绑定 2曾绑定被释放
     * <p>小程序据此区分「硬拦截」（NEVER_BOUND）与「软提示」（RELEASED）。
     */
    private PhoneState phoneState;

    /**
     * 个人介绍
     */
    private String bio;

    /**
     * 性别（0未知 1男 2女）
     */
    private Integer gender;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
