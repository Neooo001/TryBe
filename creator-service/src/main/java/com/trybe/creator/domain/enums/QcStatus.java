package com.trybe.creator.domain.enums;

import lombok.Getter;

/**
 * 模特图质检状态
 *
 * <p>质检失败不阻断保存（红线⑥ 静默降级）：FAIL 仅作提示，物品仍可继续编辑/提交。
 */
@Getter
public enum QcStatus {

    /** 待质检 */
    PENDING("PENDING", "待质检"),
    /** 通过 */
    PASS("PASS", "通过"),
    /** 警告可用 */
    WARN("WARN", "警告可用"),
    /** 不通过 */
    FAIL("FAIL", "不通过"),
    ;

    private final String code;
    private final String desc;

    QcStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
