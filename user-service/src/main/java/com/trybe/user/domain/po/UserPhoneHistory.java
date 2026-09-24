package com.trybe.user.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 曾绑定手机号历史表（append-only）
 * </p>
 *
 * <p>手机号被其他账号抢绑（归属转移）时写入，记录「被释放方 A + 曾绑手机号」，
 * 作为「历史手机号 + 密码」恢复路径的匹配依据与审计凭证。不参与唯一约束。
 *
 * @author TryBe Team
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_phone_history")
public class UserPhoneHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 曾绑定该号的账号（即被释放的 A）
     */
    private Long userId;

    /**
     * 曾绑定的手机号
     */
    private String phone;

    /**
     * 上次绑定该号的时间
     */
    private LocalDateTime boundTime;

    /**
     * 释放时间
     */
    private LocalDateTime releasedTime;

    /**
     * 释放原因：PHONE_TRANSFERRED 被其他账号抢绑
     */
    private String releaseReason;
}
