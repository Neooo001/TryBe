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
 * 用户密码表（1:1 user）
 * </p>
 *
 * <p>与手机号解耦：手机号易主后密码仍随「人」走，不迁移给新持有者，
 * 从而支撑「历史手机号 + 密码」的账号恢复路径。
 *
 * @author TryBe Team
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_password")
public class UserPassword implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID（1:1 user.id），主键非自增，落库由业务指定
     */
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    /**
     * 密码 BCrypt 哈希
     */
    private String passwordHash;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
