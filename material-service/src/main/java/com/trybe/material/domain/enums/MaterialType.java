package com.trybe.material.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.trybe.common.exception.BadRequestException;
import lombok.Getter;

/**
 * 素材类型枚举
 */
@Getter
public enum MaterialType {
    FACE("FACE", "人脸"),
    HAND("HAND", "手部"),
    ;

    @EnumValue
    private final String value;
    private final String desc;

    MaterialType(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static MaterialType of(String value) {
        for (MaterialType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new BadRequestException("无效的素材类型: " + value);
    }
}
