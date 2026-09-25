package com.trybe.user.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trybe.user.enums.UserStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Web 域用户表（{@code web_user}）—— 创作者后台账号
 *
 * <p><b>双账号域拆分后</b>：本表与小程序域 {@code wx_user} <b>零关联</b>：
 * 同一手机号可以在两表各存在一个账号，互不可见、互不影响。
 * Web 域以「手机号 + 密码」为唯一标识，不共享小程序域的
 * {@code user_identity / user_password / phone_state} 任何机制。
 *
 * <p>密码内嵌本表（不像 wx 域那样拆出 user_password）：Web 域只有手机号一种凭据、
 * 不存在「手机号易主后密码要随人走」的问题，单表即可。
 *
 * @author TryBe Team
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("web_user")
public class WebUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 登录手机号（唯一） */
    private String phone;

    /** 密码 BCrypt 哈希 */
    private String passwordHash;

    /** 昵称 */
    private String nickname;

    /** 使用状态：0 冻结 / 1 正常 */
    private UserStatus status;

    /**
     * 最近登录时间 —— 沉睡归档判定依据
     *
     * <p>注册撞占用号时：若持有者距今超过 {@code DORMANT_DAYS} 未登录，
     * 判定为「疑似二手号/弃用号」，归档老账号后放行新注册。
     */
    private LocalDateTime lastLoginTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
