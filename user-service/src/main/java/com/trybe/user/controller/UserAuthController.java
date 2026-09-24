package com.trybe.user.controller;

import com.trybe.common.auth.SmsRateLimiter;
import com.trybe.common.domain.R;
import com.trybe.common.utils.UserContext;
import com.trybe.user.domain.dto.BindPhoneDTO;
import com.trybe.user.domain.dto.CaptchaReqDTO;
import com.trybe.user.domain.dto.PasswordLoginDTO;
import com.trybe.user.domain.dto.SmsLoginDTO;
import com.trybe.user.domain.dto.SmsSendDTO;
import com.trybe.user.domain.vo.CaptchaVO;
import com.trybe.user.domain.vo.CreatorLoginVO;
import com.trybe.user.domain.vo.PhoneBindVO;
import com.trybe.user.service.AuthFacadeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 统一账号体系认证控制器 —— {@code /users/auth/**}
 *
 * <p><b>鉴权与白名单（网关 Nacos 与 {@code MvcConfig} 双处同步）</b>：
 * <ul>
 *   <li>免鉴权（4 条）：{@code /users/auth/captcha}、{@code /users/auth/sms/code}、
 *       {@code /users/auth/login/sms}、{@code /users/auth/login/password}</li>
 *   <li>需登录（2 条）：{@code /users/auth/phone/bind}、{@code /users/auth/phone}
 *       —— <b>不得</b>加入白名单</li>
 * </ul>
 *
 * <p><b>图形验证码只加在 3 处</b>：A1 captcha、A2 sms/code、A4 login/password；
 * A3 login/sms 不要图形码。
 *
 * <p>登录鉴权统一使用 {@link UserContext#requireUser()}，统一返回 {@link R}&lt;T&gt;，HTTP 一律 200。
 *
 * @author TryBe Team
 */
@Api(tags = "统一账号体系认证接口")
@RestController
@RequestMapping("/users/auth")
@RequiredArgsConstructor
@Slf4j
public class UserAuthController {

    private static final String PHONE_MASK_REGEX = "(\\d{3})\\d{4}(\\d{4})";
    private static final String PHONE_MASK_REPLACEMENT = "$1****$2";

    private final AuthFacadeService authFacadeService;

    @ApiOperation("A1 获取图形验证码（免鉴权）")
    @PostMapping("/captcha")
    public R<CaptchaVO> captcha(@RequestBody(required = false) CaptchaReqDTO dto) {
        return R.ok(authFacadeService.generateCaptcha(dto == null ? null : dto.getPhone()));
    }

    @ApiOperation("A2 发送短信验证码（免鉴权，图形码 + 三级限流）")
    @PostMapping("/sms/code")
    public R<Void> sendSmsCode(@RequestBody @Validated SmsSendDTO dto, HttpServletRequest request) {
        String clientIp = SmsRateLimiter.normalizeIp(request.getHeader("X-Forwarded-For"));
        log.info("发送短信验证码 phone={} scene={}", maskPhone(dto.getPhone()), dto.getScene());
        authFacadeService.sendSmsCode(dto, clientIp);
        return R.ok();
    }

    @ApiOperation("A3 短信验证码登录（免鉴权，未注册自动注册）")
    @PostMapping("/login/sms")
    public R<CreatorLoginVO> loginBySms(@RequestBody @Validated SmsLoginDTO dto, HttpServletRequest request) {
        String clientIp = SmsRateLimiter.normalizeIp(request.getHeader("X-Forwarded-For"));
        log.info("短信验证码登录 phone={}", maskPhone(dto.getPhone()));
        return R.ok(authFacadeService.loginBySms(dto, clientIp));
    }

    @ApiOperation("A4 密码登录（免鉴权，图形码前置）")
    @PostMapping("/login/password")
    public R<CreatorLoginVO> loginByPassword(@RequestBody @Validated PasswordLoginDTO dto, HttpServletRequest request) {
        String clientIp = SmsRateLimiter.normalizeIp(request.getHeader("X-Forwarded-For"));
        log.info("密码登录 phone={}", maskPhone(dto.getPhone()));
        return R.ok(authFacadeService.loginByPassword(dto, clientIp));
    }

    @ApiOperation("A5 绑定手机号（需登录，短信码即强凭据）")
    @PostMapping("/phone/bind")
    public R<PhoneBindVO> bindPhone(@RequestBody @Validated BindPhoneDTO dto) {
        Long userId = UserContext.requireUser();
        log.info("绑定手机号 userId={} phone={}", userId, maskPhone(dto.getPhone()));
        return R.ok(authFacadeService.bindPhone(userId, dto));
    }

    @ApiOperation("A6 查询当前手机号与归属状态（需登录）")
    @GetMapping("/phone")
    public R<PhoneBindVO> getPhone() {
        Long userId = UserContext.requireUser();
        return R.ok(authFacadeService.getPhone(userId));
    }

    /** 手机号脱敏：138****8000 */
    private String maskPhone(String phone) {
        if (phone == null) {
            return "***";
        }
        return phone.replaceAll(PHONE_MASK_REGEX, PHONE_MASK_REPLACEMENT);
    }
}
