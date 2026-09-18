package com.trybe.creator.domain.po;

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
 * 审核流水表（v1 仅记录 creator_item 的审核与上下架动作）
 * </p>
 *
 * <p>入驻审核是不写本表的（creator_application 自带审核字段）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("audit_record")
public class AuditRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 对象类型：CREATOR_ITEM */
    private String objectType;

    /** 对象ID */
    private Long objectId;

    /** 动作：SUBMIT/APPROVE/REJECT/ON_SHELF/OFF_SHELF */
    private String action;

    /** 操作人 user.id */
    private Long operatorId;

    /** 原因/备注 */
    private String reason;

    /** 操作时间 */
    private LocalDateTime createTime;
}
