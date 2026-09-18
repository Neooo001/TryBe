package com.trybe.creator.domain.enums;

import lombok.Getter;

/**
 * 物品试穿模式
 *
 * <p>MODEL / BOTH 需要在提交审核前有「已勾选肖像授权」的模特图。
 */
@Getter
public enum TryonMode {

    /** 平铺图 */
    FLAT("FLAT", "平铺图"),
    /** 模特上身图 */
    MODEL("MODEL", "模特上身图"),
    /** 两者都有 */
    BOTH("BOTH", "平铺图+模特图"),
    ;

    private final String code;
    private final String desc;

    TryonMode(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 宽松解析：未知值回退 FLAT（不因脏数据阻塞物品读取）
     */
    public static TryonMode of(String code) {
        if (code == null || code.trim().isEmpty()) {
            return FLAT;
        }
        for (TryonMode mode : values()) {
            if (mode.code.equalsIgnoreCase(code.trim())) {
                return mode;
            }
        }
        return FLAT;
    }

    /**
     * 该模式是否需要模特图（含模特上身图）
     */
    public boolean requireModelImage() {
        return this == MODEL || this == BOTH;
    }
}
