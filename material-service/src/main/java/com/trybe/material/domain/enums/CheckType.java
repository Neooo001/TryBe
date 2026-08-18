package com.trybe.material.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.trybe.common.exception.BadRequestException;
import lombok.Getter;

/**
 * 校验类型枚举
 */
@Getter
public enum CheckType {
    QUALITY("QUALITY", "质量校验"),
    FORMAT("FORMAT", "格式校验"),
    AI_FACE("AI_FACE", "人脸AI校验"),
    AI_HAND("AI_HAND", "手部AI校验"),
    ;

    @EnumValue
    private final String value;
    private final String desc;

    CheckType(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static CheckType of(String value) {
        for (CheckType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new BadRequestException("无效的校验类型: " + value);
    }
}
