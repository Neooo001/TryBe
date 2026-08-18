package com.trybe.material.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.trybe.common.exception.BadRequestException;
import lombok.Getter;

/**
 * 素材校验状态枚举
 */
@Getter
public enum MaterialStatus {
    PENDING_UPLOAD(0, "待上传"),
    CHECKING(1, "校验中"),
    PASSED(2, "校验通过"),
    FAILED(3, "校验失败"),
    ;

    @EnumValue
    private final int value;
    private final String desc;

    MaterialStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static MaterialStatus of(int value) {
        for (MaterialStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        throw new BadRequestException("无效的素材状态: " + value);
    }
}
