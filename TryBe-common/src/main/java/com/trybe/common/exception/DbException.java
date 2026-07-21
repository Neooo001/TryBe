package com.trybe.common.exception;

import com.trybe.common.domain.ErrorCodeEnum;

public class DbException extends CommonException {

    public DbException(String message) {
        super(message, ErrorCodeEnum.DB_ERROR.getCode());
    }

    public DbException(String message, Throwable cause) {
        super(message, cause, ErrorCodeEnum.DB_ERROR.getCode());
    }

    public DbException(Throwable cause) {
        super(cause, ErrorCodeEnum.DB_ERROR.getCode());
    }

    public DbException(ErrorCodeEnum codeEnum) {
        super(codeEnum);
    }

    public DbException(ErrorCodeEnum codeEnum, String customMsg) {
        super(codeEnum, customMsg);
    }
}
