package com.trybe.creator.domain.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 创作者物品状态
 *
 * <p>流转：DRAFT → PENDING → APPROVED → ON_SHELF / OFF_SHELF
 * REJECTED 可编辑后重新提交审核。
 *
 * <p>PO 中一律以 String 存储（不建枚举类型处理器），本枚举仅用于常量约束与流转校验。
 */
@Getter
public enum CreatorItemStatus {

    /** 草稿 */
    DRAFT("DRAFT", "草稿"),
    /** 待审核 */
    PENDING("PENDING", "待审"),
    /** 已通过（可上架） */
    APPROVED("APPROVED", "已通过"),
    /** 已驳回 */
    REJECTED("REJECTED", "已驳回"),
    /** 已上架 */
    ON_SHELF("ON_SHELF", "已上架"),
    /** 已下架 */
    OFF_SHELF("OFF_SHELF", "已下架"),
    ;

    private final String code;
    private final String desc;

    CreatorItemStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 宽松解析：未知值返回 null（由调用方决定降级策略）
     */
    public static CreatorItemStatus of(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(s -> s.code.equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElse(null);
    }

    /** 是否可编辑（草稿/驳回/下架） */
    public boolean editable() {
        return this == DRAFT || this == REJECTED || this == OFF_SHELF;
    }

    /** 是否可删除（与可编辑状态一致） */
    public boolean deletable() {
        return editable();
    }

    /** 是否可提交审核 */
    public boolean submittable() {
        return this == DRAFT || this == REJECTED;
    }

    /** 是否可上架 */
    public boolean canOnShelf() {
        return this == APPROVED || this == OFF_SHELF;
    }

    /** 是否可下架 */
    public boolean canOffShelf() {
        return this == ON_SHELF;
    }

    /** 是否处于待审核 */
    public boolean pending() {
        return this == PENDING;
    }
}
