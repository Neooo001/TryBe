package com.trybe.wardrobe.domain.enums;

import lombok.Getter;

/**
 * 衣服导入任务状态
 *
 * <p>与 material-service 的 CheckStatus 保持同一套语义，便于统一运维观测。
 */
@Getter
public enum ImportTaskStatus {

    PENDING(0, "待处理"),
    PROCESSING(1, "处理中"),
    SUCCESS(2, "成功"),
    FAILED(3, "失败"),
    ;

    private final int code;
    private final String desc;

    ImportTaskStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ImportTaskStatus of(Integer code) {
        if (code == null) {
            return PENDING;
        }
        for (ImportTaskStatus item : values()) {
            if (item.code == code) {
                return item;
            }
        }
        return PENDING;
    }
}
