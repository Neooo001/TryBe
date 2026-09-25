package com.trybe.user.domain.po;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trybe.user.enums.UserStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Web 域沉睡归档表（{@code web_user_archive}）
 *
 * <p>归档方式选用<b>整行搬迁</b>而非「主表 phone 改写墓碑值」，理由：
 * <ol>
 *   <li>主表 {@code phone} 保持干净：墓碑值会污染所有按手机号查询/格式校验的代码路径</li>
 *   <li>{@code UNIQUE(phone)} 天然兜底释放语义：整行迁出即释放，与 wx 域
 *       {@code bindOrTransferInTx} 的「FOR UPDATE + 唯一键兜底」风格一致</li>
 *   <li>可审计、可回退：答辩/排障时能把归档行搬回主表</li>
 * </ol>
 *
 * @author TryBe Team
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("web_user_archive")
public class WebUserArchive implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 原 web_user 主键（非自增，落库由业务指定） */
    @TableId(value = "id", type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    private Long id;

    private String phone;

    private String passwordHash;

    private String nickname;

    private UserStatus status;

    private LocalDateTime lastLoginTime;

    private LocalDateTime createTime;

    /** 归档原因 */
    private String archiveReason;

    private LocalDateTime archivedTime;

    /**
     * 由主表行构造归档行
     */
    public static WebUserArchive of(WebUser user, String reason) {
        return new WebUserArchive()
                .setId(user.getId())
                .setPhone(user.getPhone())
                .setPasswordHash(user.getPasswordHash())
                .setNickname(user.getNickname())
                .setStatus(user.getStatus())
                .setLastLoginTime(user.getLastLoginTime())
                .setCreateTime(user.getCreateTime())
                .setArchiveReason(reason);
    }
}
