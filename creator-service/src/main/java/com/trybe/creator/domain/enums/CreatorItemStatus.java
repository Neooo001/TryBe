package com.trybe.creator.domain.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 创作者物品状态
 *
 * <p>流转：DRAFT --发布(I6)--> ON_SHELF --下架(I8)--> OFF_SHELF --上架(I7)--> ON_SHELF
 * 已下架(OFF_SHELF)也可再次发布(I6)直接回到 ON_SHELF；草稿与已下架均可编辑(I4)/删除(I5)。
 *
 * <p>已下线的旧状态：PENDING（待审）、APPROVED（审核通过）、REJECTED（已驳回）。
 * 物品审核链路整体移除后状态机收敛为「发布即上架」，存量数据由 SQL 迁移脚本收敛到三值。
 *
 * <p>注意：PENDING 同名值仍存在于 {@link QcStatus}（模特图质检 PENDING/PASS/WARN/FAIL），
 * 与本枚举无关，请勿混淆。（另一处同名值 creator_application.status 已随「创作者网页自助注册」
 * 下线而消失，现仅剩上述一处。）
 *
 * <p>PO 中一律以 String 存储（不建枚举类型处理器），本枚举仅用于常量约束与流转校验。
 */
@Getter
public enum CreatorItemStatus {

    /** 草稿 */
    DRAFT("DRAFT", "草稿"),
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

    /** 是否可编辑（草稿/已下架） */
    public boolean editable() {
        return this == DRAFT || this == OFF_SHELF;
    }

    /** 是否可删除（与可编辑状态一致） */
    public boolean deletable() {
        return editable();
    }

    /** 是否可发布（发布即上架：DRAFT/OFF_SHELF → ON_SHELF） */
    public boolean publishable() {
        return this == DRAFT || this == OFF_SHELF;
    }

    /** 是否可上架 */
    public boolean canOnShelf() {
        return this == OFF_SHELF;
    }

    /** 是否可下架 */
    public boolean canOffShelf() {
        return this == ON_SHELF;
    }
}
