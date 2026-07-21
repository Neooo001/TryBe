package com.trybe.common.exception;

import com.trybe.common.domain.ErrorCodeEnum;

/**
 * 认证异常 — 双 Token 体系专用
 *
 * <p>构造重载覆盖两种场景：
 * <ul>
 *   <li>过期场景：使用 {@code ACCESS_TOKEN_EXPIRE (40101)} — 前端自动刷新</li>
 *   <li>非法/签名错误/Refresh过期：使用 {@code TOKEN_INVALID_RELOGIN (40102)} — 前端引导重新登录</li>
 * </ul>
 *
 * <p>保留旧的 {@code UnauthorizedException(String)} 构造，默认使用 {@code NOT_LOGIN (301)}，
 * 兼容非 Token 场景的未登录提示。
 *
 * @author TryBe Team
 */
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
