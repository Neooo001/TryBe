package com.trybe.wardrobe.domain.enums;

import lombok.Getter;

/**
 * 衣服穿着状态枚举
 *
 * <p>对应小程序衣橱详情页「状态」选择器：在穿 / 种草 / 闲置 / 舍弃。
 */
@Getter
public enum WearStatus {

    WEARING("WEARING", "在穿"),
    WISHLIST("WISHLIST", "种草"),
    IDLE("IDLE", "闲置"),
    DISCARD("DISCARD", "舍弃"),
    ;

    private final String value;
    private final String desc;

    WearStatus(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    /**
     * 宽松解析，非法值回落为 WEARING（避免历史数据或前端漏传导致报错）
     */
    public static WearStatus of(String value) {
        if (value == null) {
            return WEARING;
        }
        for (WearStatus item : values()) {
            if (item.value.equalsIgnoreCase(value.trim()) || item.desc.equals(value.trim())) {
                return item;
            }
        }
        return WEARING;
    }
}
