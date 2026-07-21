package com.trybe.common.exception;

import com.trybe.common.domain.ErrorCodeEnum;

public class UnauthorizedException extends CommonException {

    public UnauthorizedException(String message) {
        super(message, ErrorCodeEnum.NOT_LOGIN.getCode());
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause, ErrorCodeEnum.NOT_LOGIN.getCode());
    }

    public UnauthorizedException(Throwable cause) {
        super(cause, ErrorCodeEnum.NOT_LOGIN.getCode());
    }

    public UnauthorizedException(ErrorCodeEnum codeEnum) {
        super(codeEnum);
    }

    public UnauthorizedException(ErrorCodeEnum codeEnum, String customMsg) {
        super(codeEnum, customMsg);
    }
}
