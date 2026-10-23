package com.trybe.user.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trybe.user.enums.PhoneState;
import com.trybe.user.enums.RegisterSource;
import com.trybe.user.enums.UserStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 统一用户表（{@code app_user}，账号体系 v3）
 *
 * <p><b>不再区分创作者与普通用户</b>：一个人一个账号，小程序与创作者后台共用同一行。
 * 本表只放「人」的属性，不承载任何登录凭据：
 * <ul>
 *   <li>手机号 → {@code user_identity(identity_type='PHONE')}</li>
 *   <li>openid → {@code user_identity(identity_type='WECHAT_OPENID', app_id=...)}</li>
 *   <li>密码   → {@code user_password(user_id)}</li>
 * </ul>
 *
 * <p><b>手机号是唯一身份锚点</b>：{@link #registerSource} 记录账号首次诞生端，
 * 「手机号归属哪一端，账号数据即归属该端」；跨端登录只复用账号，不迁移数据。
 *
 * @author TryBe Team
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("app_user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 昵称（Web 首次注册时随机生成，用户可改）
     */
    private String nickname;

    /**
     * 头像 OSS objectKey
     */
    private String avatar;

    /**
     * 账号状态：0冻结 1正常 2已合并（并入其他账号，仅留审计）
     */
    private UserStatus status;

    /**
     * 微信开放平台 unionid（预留字段，个人主体拿不到）
     */
    private String unionid;

    /**
     * 手机号归属状态：0从未绑定 1已绑定 2曾绑定被释放
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
     * 首次注册端：WX 小程序 / WEB 创作者后台
     */
    private RegisterSource registerSource;

    /**
     * 最近登录时间（沉睡 180 天判定依据：超期后手机号可被新用户接管）
     */
    private LocalDateTime lastLoginTime;

    /**
     * 合并目标账号ID（status=2 时有效，仅审计，不参与登录）
     */
    private Long mergedIntoUserId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
