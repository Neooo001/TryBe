package com.trybe.user.controller;

import com.trybe.common.auth.SmsRateLimiter;
import com.trybe.common.domain.R;
import com.trybe.common.utils.UserContext;
import com.trybe.user.domain.dto.BindPhoneDTO;
import com.trybe.user.domain.dto.CaptchaReqDTO;
import com.trybe.user.domain.dto.PasswordLoginDTO;
import com.trybe.user.domain.dto.SmsSendAuthedDTO;
import com.trybe.user.domain.dto.SmsSendDTO;
import com.trybe.user.domain.vo.CaptchaVO;
import com.trybe.user.domain.vo.PhoneBindVO;
import com.trybe.user.domain.vo.UserLoginVO;
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
 * 小程序（wx 域）认证控制器 —— {@code /users/auth/**}
 *
 * <p><b>双账号域拆分后</b>：本控制器只服务小程序，表为 {@code wx_user}；
 * Web 创作者后台走 {@code /web/auth/**}（表 {@code web_user}），两端零交集。
 * 短信验证码已<b>不再用于登录</b>（原 {@code /users/auth/login/sms} 已删除），
 * 仅用于「绑定手机号」与「设置密码」两个场景（见 {@code SmsScene}）。
 *
 * <p><b>鉴权与白名单（网关本地 yml 与 {@code MvcConfig} 双处同步）</b>：
 * <ul>
 *   <li>免鉴权（3 条）：{@code /users/auth/captcha}、{@code /users/auth/sms/code}、
 *       {@code /users/auth/login/password}</li>
 *   <li>需登录（3 条）：{@code /users/auth/sms/code/authed}、{@code /users/auth/phone/bind}、
 *       {@code /users/auth/phone} —— <b>不得</b>加入白名单</li>
 * </ul>
 *
 * <p>{@code /users/auth/sms/code/authed} 是 A2 的「登录版」：免鉴权版必须带图形码，
 * 本版改由登录态承担防线，因此<b>绝对不能</b>进白名单 —— 一旦放行就等于开放一个
 * 免图形码、只受 100 条/天约束的发码入口。
 *
 * <p><b>图形验证码只加在 2 处</b>：{@code captcha}、{@code sms/code}、
 * {@code login/password}（密码登录需防撞库）。
 *
 * <p>登录鉴权统一使用 {@link UserContext#requireUser()}，统一返回 {@link R}&lt;T&gt;，HTTP 一律 200。
 *
 * @author TryBe Team
 */
@Api(tags = "小程序认证接口（wx 域）")
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

    @ApiOperation("A2 发送短信验证码（免鉴权，图形码 + 限流）")
    @PostMapping("/sms/code")
    public R<Void> sendSmsCode(@RequestBody @Validated SmsSendDTO dto, HttpServletRequest request) {
        String clientIp = SmsRateLimiter.normalizeIp(request.getHeader("X-Forwarded-For"));
        log.info("发送短信验证码 phone={} scene={}", maskPhone(dto.getPhone()), dto.getScene());
        authFacadeService.sendSmsCode(dto, clientIp);
        return R.ok();
    }

    @ApiOperation("A2b 发送短信验证码（需登录，无图形码；用于绑号 / 设置密码）")
    @PostMapping("/sms/code/authed")
    public R<Void> sendSmsCodeAuthed(@RequestBody @Validated SmsSendAuthedDTO dto, HttpServletRequest request) {
        // 网关已强制登录（该路径不在白名单），此处为防御性兜底，防内网直连绕过网关
        Long userId = UserContext.requireUser();
        String clientIp = SmsRateLimiter.normalizeIp(request.getHeader("X-Forwarded-For"));
        log.info("发送短信验证码(已登录) userId={} scene={}", userId, dto.getScene());
        authFacadeService.sendSmsCodeForLoginUser(userId, dto, clientIp);
        return R.ok();
    }

    @ApiOperation("密码登录（wx 域，免鉴权，图形码前置）")
    @PostMapping("/login/password")
    public R<UserLoginVO> loginByPassword(@RequestBody @Validated PasswordLoginDTO dto, HttpServletRequest request) {
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
