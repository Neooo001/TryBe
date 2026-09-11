package com.trybe.wardrobe.domain.enums;

import com.trybe.common.exception.BadRequestException;
import lombok.Getter;

/**
 * 衣柜条目来源枚举
 */
@Getter
public enum WardrobeSource {
    TAOBAO_IMPORT("TAOBAO_IMPORT", "淘宝导入"),
    MANUAL("MANUAL", "手动添加"),
    CUSTOM_OUTFIT("CUSTOM_OUTFIT", "自定义穿搭"),
    ;

    private final String value;
    private final String desc;

    WardrobeSource(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static WardrobeSource of(String value) {
        for (WardrobeSource source : values()) {
            if (source.value.equals(value)) {
                return source;
            }
        }
        throw new BadRequestException("无效的衣柜来源: " + value);
    }
}
