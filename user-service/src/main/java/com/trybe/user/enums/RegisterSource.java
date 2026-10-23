package com.trybe.user.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.trybe.common.exception.BadRequestException;
import lombok.Getter;

/**
 * 账号首次注册端（{@code app_user.register_source}）
 *
 * <p>语义：手机号归属哪一端，账号数据即归属该端。
 * 账号一旦建立就<b>不再改变</b>来源，跨端登录只复用、不迁移。
 *
 * @author TryBe Team
 */
@Getter
public enum RegisterSource {

    /** 小程序微信一键登录建号 */
    WX("WX", "小程序"),

    /** 创作者后台手机号注册建号 */
    WEB("WEB", "创作者后台");

    @EnumValue
    private final String value;

    private final String desc;

    RegisterSource(String value, String desc) {
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
    public static RegisterSource of(String value) {
        for (RegisterSource source : values()) {
            if (source.value.equals(value)) {
                return source;
            }
        }
        throw new BadRequestException("注册来源错误");
    }
}
