package com.trybe.user.controller;

import com.trybe.common.auth.RealmConstants;
import com.trybe.common.auth.SmsRateLimiter;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.domain.R;
import com.trybe.common.exception.ForbiddenException;
import com.trybe.common.utils.UserContext;
import com.trybe.user.domain.dto.CaptchaReqDTO;
import com.trybe.user.domain.dto.SmsSendDTO;
import com.trybe.user.domain.dto.WebLoginDTO;
import com.trybe.user.domain.dto.WebPasswordChangeDTO;
import com.trybe.user.domain.dto.WebRegisterDTO;
import com.trybe.user.domain.dto.WebSetPasswordDTO;
import com.trybe.user.domain.dto.WebSmsLoginDTO;
import com.trybe.user.domain.vo.CaptchaVO;
import com.trybe.user.domain.vo.WebLoginVO;
import com.trybe.user.service.WebAuthService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * Web 端（创作者后台）认证控制器 —— {@code /web/auth/**}
 *
 * <p><b>账号体系 v3</b>：与小程序共用 {@code app_user}，令牌 {@code realm=web}
 * 仅表示「Web 端会话」，同一 user_id 两端可并存。
 *
 * <p><b>白名单</b>（网关 application.yml 与 {@code MvcConfig} 双处同步）：
 * 免鉴权 5 条 —— {@code captcha}、{@code sms/code}、{@code register}、{@code login}、{@code login/sms}；
 * 需登录 3 条 —— {@code sms/code/set-password}、{@code password/set}、{@code password/change}。
 *
 * @author TryBe Team
 */
@Api(tags = "Web 创作者后台认证接口")
@RestController
@RequestMapping("/web/auth")
@RequiredArgsConstructor
@Slf4j
public class WebAuthController {

    private static final String PHONE_MASK_REGEX = "(\\d{3})\\d{4}(\\d{4})";
    private static final String PHONE_MASK_REPLACEMENT = "$1****$2";

    private final WebAuthService webAuthService;

    @ApiOperation("获取图形验证码（免鉴权）")
    @PostMapping("/captcha")
    public R<CaptchaVO> captcha(@RequestBody(required = false) CaptchaReqDTO dto) {
        return R.ok(webAuthService.generateCaptcha(dto == null ? null : dto.getPhone()));
    }

    @ApiOperation("发送短信验证码（免鉴权，图形码 + 限流）")
    @PostMapping("/sms/code")
    public R<Void> sendSmsCode(@RequestBody @Validated SmsSendDTO dto, HttpServletRequest request) {
        String clientIp = SmsRateLimiter.normalizeIp(request.getHeader("X-Forwarded-For"));
        log.info("Web 发送短信验证码 phone={} scene={}", maskPhone(dto.getPhone()), dto.getScene());
        webAuthService.sendSmsCode(dto, clientIp);
        return R.ok();
    }

    @ApiOperation("注册（免鉴权，手机号+短信码，注册即登录）")
    @PostMapping("/register")
    public R<WebLoginVO> register(@RequestBody @Validated WebRegisterDTO dto, HttpServletRequest request) {
        String clientIp = SmsRateLimiter.normalizeIp(request.getHeader("X-Forwarded-For"));
        log.info("Web 注册 phone={}", maskPhone(dto.getPhone()));
        return R.ok(webAuthService.register(dto, clientIp));
    }

    @ApiOperation("短信验证码登录（免鉴权）")
    @PostMapping("/login/sms")
    public R<WebLoginVO> loginBySms(@RequestBody @Validated WebSmsLoginDTO dto, HttpServletRequest request) {
        String clientIp = SmsRateLimiter.normalizeIp(request.getHeader("X-Forwarded-For"));
        log.info("Web 短信登录 phone={}", maskPhone(dto.getPhone()));
        return R.ok(webAuthService.loginBySms(dto, clientIp));
    }

    @ApiOperation("密码登录（免鉴权，手机号+密码+图形码）")
    @PostMapping("/login")
    public R<WebLoginVO> login(@RequestBody @Validated WebLoginDTO dto, HttpServletRequest request) {
        String clientIp = SmsRateLimiter.normalizeIp(request.getHeader("X-Forwarded-For"));
        log.info("Web 密码登录 phone={}", maskPhone(dto.getPhone()));
        return R.ok(webAuthService.login(dto, clientIp));
    }

    @ApiOperation("发送设置密码验证码（需登录，免图形码，发到已绑定手机号）")
    @PostMapping("/sms/code/set-password")
    public R<Void> sendSetPasswordCode(HttpServletRequest request) {
        Long userId = requireWebRealmUser();
        String clientIp = SmsRateLimiter.normalizeIp(request.getHeader("X-Forwarded-For"));
        log.info("Web 发送设置密码验证码 userId={}", userId);
        webAuthService.sendSetPasswordCode(userId, clientIp);
        return R.ok();
    }

    @ApiOperation("设置密码（需登录，短信码验证）")
    @PostMapping("/password/set")
    public R<Void> setPassword(@RequestBody @Validated WebSetPasswordDTO dto) {
        Long userId = requireWebRealmUser();
        webAuthService.setPassword(userId, dto);
        return R.ok();
    }

    @ApiOperation("修改密码（需登录，凭旧密码验证）")
    @PostMapping("/password/change")
    public R<Void> changePassword(@RequestBody @Validated WebPasswordChangeDTO dto) {
        Long userId = requireWebRealmUser();
        webAuthService.changePassword(userId, dto);
        return R.ok();
    }

    /**
     * 取当前用户并校验 realm=web（纵深防御：网关已按路径校验，此处再校验防内网直连绕过）
     */
    private Long requireWebRealmUser() {
        Long userId = UserContext.requireUser();
        if (!RealmConstants.WEB.equals(UserContext.getRealm())) {
            throw new ForbiddenException(ErrorCodeEnum.CROSS_REALM_FORBIDDEN, "无权访问该端接口");
        }
        return userId;
    }

    /** 手机号脱敏：138****8000 */
    private String maskPhone(String phone) {
        if (phone == null) {
            return "***";
        }
        return phone.replaceAll(PHONE_MASK_REGEX, PHONE_MASK_REPLACEMENT);
    }
}
