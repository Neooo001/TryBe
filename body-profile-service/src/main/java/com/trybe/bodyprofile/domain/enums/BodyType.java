package com.trybe.bodyprofile.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.trybe.common.exception.BadRequestException;
import lombok.Getter;

/**
 * 体型标签枚举
 */
@Getter
public enum BodyType {
    STRAIGHT("STRAIGHT", "直筒型"),
    PEAR("PEAR", "梨型"),
    APPLE("APPLE", "苹果型"),
    HOURGLASS("HOURGLASS", "沙漏型"),
    INVERTED_TRIANGLE("INVERTED_TRIANGLE", "倒三角型"),
    ;

    @EnumValue
    private final String value;
    private final String desc;

    BodyType(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static BodyType of(String value) {
        for (BodyType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new BadRequestException("无效的体型标签: " + value);
    }
}
