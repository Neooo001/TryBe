package com.trybe.common.advice;

import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.domain.R;
import com.trybe.common.exception.BadRequestException;
import com.trybe.common.exception.CommonException;
import com.trybe.common.exception.DbException;
import com.trybe.common.exception.UnauthorizedException;
import com.trybe.common.utils.WebUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.util.NestedServletException;

import java.net.BindException;

/**
 * 全局异常通知
 *
 * <p>核心原则：所有异常统一返回 HTTP 200，通过响应体中的 {@code code} 字段区分业务结果。
 * 前端只需检查 {@code data.code}，不再依赖 HTTP 状态码做登出/跳转等分支逻辑。
 *
 * <pre>
 * 业务码分段：
 *   1xx = 客户端参数错误
 *   3xx = 身份认证与会话（前端收到后触发清除 Token 跳转登录页）
 *   4xx = 业务逻辑错误
 *   5xx = 服务端系统异常
 * </pre>
 *
 * @author TryBe Team
 */
@RestControllerAdvice
@Slf4j
public class CommonExceptionAdvice {

    /**
     * 认证异常独立处理 — 双 Token 体系
     *
     * <p>返回的 code 取决于异常构造时传入的 ErrorCodeEnum：
     * <ul>
     *   <li>{@code ACCESS_TOKEN_EXPIRE (40101)} → 前端自动刷新 AccessToken</li>
     *   <li>{@code TOKEN_INVALID_RELOGIN (40102)} → 前端引导重新登录</li>
     *   <li>{@code NOT_LOGIN (301)} → 旧兼容场景</li>
     * </ul>
     *
     * <p>日志中 Token 信息由 Gateway 层脱敏，Service 层不再重复打印完整 Token。
     */
    @ExceptionHandler(UnauthorizedException.class)
    public R<Void> handleUnauthorizedException(UnauthorizedException e) {
        log.warn("认证异常 -> code={} msg={}", e.getCode(), e.getMessage());
        log.debug("", e);
        return new R<>(e.getCode(), e.getMessage(), null);
    }

    // ======================== 自定义业务异常 ========================

    @ExceptionHandler(DbException.class)
    public R<Void> handleDbException(DbException e) {
        log.error("mysql数据库操作异常 -> ", e);
        return R.error(e);
    }

    @ExceptionHandler(CommonException.class)
    public R<Void> handleCommonException(CommonException e) {
        log.error("自定义异常 -> {} , 异常原因：{}  ", e.getClass().getName(), e.getMessage());
        log.debug("", e);
        return R.error(e);
    }

    // ======================== 参数校验异常 ========================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getAllErrors()
                .stream().map(ObjectError::getDefaultMessage)
                .reduce((a, b) -> a + "|" + b)
                .orElse("参数校验失败");
        log.error("请求参数校验异常 -> {}", msg);
        log.debug("", e);
        return R.error(ErrorCodeEnum.PARAM_ERROR, msg);
    }

    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) {
        log.error("请求参数绑定异常 ->BindException， {}", e.getMessage());
        log.debug("", e);
        return R.error(ErrorCodeEnum.PARAM_FORMAT_ERR, "请求参数格式错误");
    }

    @ExceptionHandler(NestedServletException.class)
    public R<Void> handleNestedServletException(NestedServletException e) {
        log.error("参数异常 -> NestedServletException，{}", e.getMessage());
        log.debug("", e);
        return R.error(ErrorCodeEnum.PARAM_ERROR, "请求参数处理异常");
    }

    // ======================== Spring MVC 框架层异常 ========================

    /**
     * Content-Type 不匹配（如用 JSON 请求 multipart 接口）
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public R<Void> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.warn("不支持的 Content-Type: {}", e.getContentType());
        return R.error(ErrorCodeEnum.PARAM_FORMAT_ERR, "请求格式不支持，请检查 Content-Type");
    }

    /**
     * HTTP 方法不支持（如用 POST 请求 PUT 接口）
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("不支持的请求方法: {}", e.getMethod());
        return R.error(ErrorCodeEnum.PARAM_ERROR, "请求方式不支持");
    }

    /**
     * 缺少必填的 @RequestParam 参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数: {}", e.getParameterName());
        return R.error(ErrorCodeEnum.PARAM_EMPTY, "缺少必填参数：" + e.getParameterName());
    }

    /**
     * 缺少 multipart 文件
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public R<Void> handleMissingPart(MissingServletRequestPartException e) {
        log.warn("缺少文件上传参数: {}", e.getRequestPartName());
        return R.error(ErrorCodeEnum.PARAM_EMPTY, "缺少文件参数：" + e.getRequestPartName());
    }

    /**
     * JSON 解析失败
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体 JSON 解析失败: {}", e.getMessage());
        return R.error(ErrorCodeEnum.JSON_PARSE_ERR, "请求体格式错误，请检查 JSON 格式");
    }

    /**
     * 404 路径不存在
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public R<Void> handleNoHandlerFound(NoHandlerFoundException e) {
        log.warn("请求路径不存在: {} {}", e.getHttpMethod(), e.getRequestURL());
        return R.error(ErrorCodeEnum.DATA_NOT_FOUND, "请求路径不存在");
    }

    // ======================== 兜底异常 ========================

    @ExceptionHandler(Exception.class)
    public R<Void> handleRuntimeException(Exception e) {
        log.error("其他异常 uri : {} -> ", WebUtils.getRequest().getRequestURI(), e);
        return R.error(ErrorCodeEnum.SERVER_ERROR);
    }
}
