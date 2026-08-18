package com.trybe.material.service.check;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <p>
 * 校验结果
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckResult {

    /** 是否通过 */
    private boolean passed;

    /** 结果描述 */
    private String message;

    /** 详细结果（JSON格式） */
    private String detail;

    public static CheckResult pass(String message, String detail) {
        return CheckResult.builder().passed(true).message(message).detail(detail).build();
    }

    public static CheckResult fail(String message, String detail) {
        return CheckResult.builder().passed(false).message(message).detail(detail).build();
    }
}
