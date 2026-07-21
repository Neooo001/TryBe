package com.trybe.common.exception;

import com.trybe.common.domain.ErrorCodeEnum;

public class BizIllegalException extends CommonException {

    public BizIllegalException(String message) {
        super(message, ErrorCodeEnum.BIZ_ERROR.getCode());
    }

    public BizIllegalException(String message, Throwable cause) {
        super(message, cause, ErrorCodeEnum.BIZ_ERROR.getCode());
    }

    public BizIllegalException(Throwable cause) {
        super(cause, ErrorCodeEnum.BIZ_ERROR.getCode());
    }

    public BizIllegalException(ErrorCodeEnum codeEnum) {
        super(codeEnum);
    }

    public BizIllegalException(ErrorCodeEnum codeEnum, String customMsg) {
        super(codeEnum, customMsg);
    }
}
