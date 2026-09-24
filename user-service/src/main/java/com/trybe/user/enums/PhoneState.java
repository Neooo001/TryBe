package com.trybe.user.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.trybe.common.exception.BadRequestException;
import lombok.Getter;

/**
 * 手机号归属状态（单一事实字段）
 *
 * <p>前端据此区分「硬拦截」与「软提示」：
 * <ul>
 *   <li>{@link #NEVER_BOUND}(0)：从未绑定过手机号 → 小程序首登硬拦截强制绑号</li>
 *   <li>{@link #BOUND}(1)：当前已绑定手机号 → 正常</li>
 *   <li>{@link #RELEASED}(2)：曾绑定过、已被释放 → 小程序软提示补绑，不拦截</li>
 * </ul>
 *
 * @author TryBe Team
 */
@Getter
public enum PhoneState {

    /** 从未绑定过手机号（小程序首登 → 硬拦截强制绑号） */
    NEVER_BOUND(0, "从未绑定"),

    /** 当前已绑定手机号 */
    BOUND(1, "已绑定"),

    /** 曾绑定过、已被释放（软提示补绑） */
    RELEASED(2, "曾绑定被释放");

    /** 落库值（对应 user.phone_state） */
    @EnumValue
    private final int value;

    private final String desc;

    PhoneState(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    /**
     * 由落库值解析枚举。
     *
     * @param value 数据库存值
     * @return 对应枚举
     * @throws BadRequestException 非法值
     */
    public static PhoneState of(int value) {
        for (PhoneState state : values()) {
            if (state.value == value) {
                return state;
            }
        }
        throw new BadRequestException("手机号归属状态错误");
    }
}
