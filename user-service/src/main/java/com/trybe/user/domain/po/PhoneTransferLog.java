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
 * 手机号归属转移日志（幂等键 + 审计）
 * </p>
 *
 * <p>{@code uk_transfer_no} 保证同一请求不重复执行；记录 {@code from_user_id → to_user_id}
 * 的完整流转链路，便于排查「手机号被谁抢走」类问题。
 *
 * @author TryBe Team
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("phone_transfer_log")
public class PhoneTransferLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 幂等键（服务端按 B+phone+时间窗生成）
     */
    private String transferNo;

    /**
     * 被转移的手机号
     */
    private String phone;

    /**
     * 被释放方 A（无占用者时为 NULL）
     */
    private Long fromUserId;

    /**
     * 获得方 B
     */
    private Long toUserId;

    /**
     * 触发场景：BIND_PHONE
     */
    private String scene;

    /**
     * 转移时间
     */
    private LocalDateTime createTime;
}
