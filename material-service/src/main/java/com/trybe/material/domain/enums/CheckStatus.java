package com.trybe.material.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.trybe.common.exception.BadRequestException;
import lombok.Getter;

/**
 * 校验结果枚举
 */
@Getter
public enum CheckStatus {
    PENDING(0, "待处理"),
    PROCESSING(1, "处理中"),
    PASSED(2, "通过"),
    FAILED(3, "失败"),
    ;

    @EnumValue
    private final int value;
    private final String desc;

    CheckStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static CheckStatus of(int value) {
        for (CheckStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        throw new BadRequestException("无效的校验结果: " + value);
    }
}
