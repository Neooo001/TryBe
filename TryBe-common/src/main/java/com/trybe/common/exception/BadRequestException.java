package com.trybe.common.exception;

import com.trybe.common.domain.ErrorCodeEnum;

public class BadRequestException extends CommonException {

    public BadRequestException(String message) {
        super(message, ErrorCodeEnum.PARAM_ERROR.getCode());
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause, ErrorCodeEnum.PARAM_ERROR.getCode());
    }

    public BadRequestException(Throwable cause) {
        super(cause, ErrorCodeEnum.PARAM_ERROR.getCode());
    }

    public BadRequestException(ErrorCodeEnum codeEnum) {
        super(codeEnum);
    }

    public BadRequestException(ErrorCodeEnum codeEnum, String customMsg) {
        super(codeEnum, customMsg);
    }
}
