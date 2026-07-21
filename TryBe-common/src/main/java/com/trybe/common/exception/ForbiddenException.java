package com.trybe.common.exception;

import com.trybe.common.domain.ErrorCodeEnum;

public class ForbiddenException extends CommonException {

    public ForbiddenException(String message) {
        super(message, ErrorCodeEnum.FORBIDDEN.getCode());
    }

    public ForbiddenException(String message, Throwable cause) {
        super(message, cause, ErrorCodeEnum.FORBIDDEN.getCode());
    }

    public ForbiddenException(Throwable cause) {
        super(cause, ErrorCodeEnum.FORBIDDEN.getCode());
    }

    public ForbiddenException(ErrorCodeEnum codeEnum) {
        super(codeEnum);
    }

    public ForbiddenException(ErrorCodeEnum codeEnum, String customMsg) {
        super(codeEnum, customMsg);
    }
}
