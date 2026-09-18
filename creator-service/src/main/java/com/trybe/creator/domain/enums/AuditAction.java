package com.trybe.creator.domain.enums;

import lombok.Getter;

/**
 * 审核流水动作
 */
@Getter
public enum AuditAction {

    /** 提交审核 */
    SUBMIT("SUBMIT", "提交审核"),
    /** 审核通过 */
    APPROVE("APPROVE", "通过"),
    /** 审核驳回 */
    REJECT("REJECT", "驳回"),
    /** 上架 */
    ON_SHELF("ON_SHELF", "上架"),
    /** 下架 */
    OFF_SHELF("OFF_SHELF", "下架"),
    ;

    private final String code;
    private final String desc;

    AuditAction(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
