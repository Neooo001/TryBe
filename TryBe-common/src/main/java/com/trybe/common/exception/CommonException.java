package com.trybe.common.exception;

import com.trybe.common.domain.ErrorCodeEnum;
import lombok.Getter;

@Getter
public class CommonException extends RuntimeException{
    private int code;

    public CommonException(String message, int code) {
        super(message);
        this.code = code;
    }

    public CommonException(String message, Throwable cause, int code) {
        super(message, cause);
        this.code = code;
    }

    public CommonException(Throwable cause, int code) {
        super(cause);
        this.code = code;
    }

    // 根据枚举构造异常
    public CommonException(ErrorCodeEnum codeEnum) {
        super(codeEnum.getMsg());
        this.code = codeEnum.getCode();
    }

    // 枚举 + 自定义提示文案（比如指定哪个参数为空）
    public CommonException(ErrorCodeEnum codeEnum, String customMsg) {
        super(customMsg);
        this.code = codeEnum.getCode();
    }
}
