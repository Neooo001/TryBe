package com.trybe.common.domain;

import com.trybe.common.exception.CommonException;
import lombok.Data;


@Data
public class R<T> {
    private int code;
    private String msg;
    private T data;

    public static R<Void> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(200, "OK", data);
    }

    public static <T> R<T> error(String msg) {
        return new R<>(500, msg, null);
    }

    public static <T> R<T> error(int code, String msg) {
        return new R<>(code, msg, null);
    }

    public static <T> R<T> error(CommonException e) {
        return new R<>(e.getCode(), e.getMessage(), null);
    }

    /**
     * 根据 ErrorCodeEnum 构造错误响应，无侵入替换 R.error(int, String)
     */
    public static <T> R<T> error(ErrorCodeEnum codeEnum) {
        return new R<>(codeEnum.getCode(), codeEnum.getMsg(), null);
    }

    /**
     * 根据 ErrorCodeEnum + 自定义提示文案构造错误响应
     */
    public static <T> R<T> error(ErrorCodeEnum codeEnum, String customMsg) {
        return new R<>(codeEnum.getCode(), customMsg, null);
    }

    public R() {
    }

    public R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public boolean success(){
        return code == 200;
    }
}
