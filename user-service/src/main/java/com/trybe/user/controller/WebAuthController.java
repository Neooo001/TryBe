package com.trybe.user.controller;

import com.trybe.common.auth.RealmConstants;
import com.trybe.common.auth.SmsRateLimiter;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.domain.R;
import com.trybe.common.exception.ForbiddenException;
import com.trybe.common.utils.UserContext;
import com.trybe.user.domain.dto.CaptchaReqDTO;
import com.trybe.user.domain.dto.WebLoginDTO;
import com.trybe.user.domain.dto.WebPasswordChangeDTO;
import com.trybe.user.domain.dto.WebRegisterDTO;
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
 * Web 域认证控制器 —— {@code /web/auth/**}
 *
 * <p><b>与 wx 域零关联</b>：只读写 {@code web_user}，令牌 realm=web。
 *
 * <p><b>白名单</b>（网关 application.yml 与 {@code MvcConfig} 双处同步）：
 * 免鉴权 3 条 —— {@code /web/auth/captcha}、{@code /web/auth/register}、{@code /web/auth/login}；
 * 需登录 1 条 —— {@code /web/auth/password/change}（<b>不得</b>加入白名单）。
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

    @ApiOperation("注册（免鉴权，无短信验证，注册即登录）")
    @PostMapping("/register")
    public R<WebLoginVO> register(@RequestBody @Validated WebRegisterDTO dto, HttpServletRequest request) {
        String clientIp = SmsRateLimiter.normalizeIp(request.getHeader("X-Forwarded-For"));
        log.info("Web 注册 phone={}", maskPhone(dto.getPhone()));
        return R.ok(webAuthService.register(dto, clientIp));
    }

    @ApiOperation("登录（免鉴权，手机号+密码+图形码）")
    @PostMapping("/login")
    public R<WebLoginVO> login(@RequestBody @Validated WebLoginDTO dto, HttpServletRequest request) {
        String clientIp = SmsRateLimiter.normalizeIp(request.getHeader("X-Forwarded-For"));
        log.info("Web 登录 phone={}", maskPhone(dto.getPhone()));
        return R.ok(webAuthService.login(dto, clientIp));
    }

    @ApiOperation("修改密码（需登录，凭旧密码验证）")
    @PostMapping("/password/change")
    public R<Void> changePassword(@RequestBody @Validated WebPasswordChangeDTO dto) {
        Long userId = UserContext.requireUser();
        if (!RealmConstants.WEB.equals(UserContext.getRealm())) {
            // 纵深防御：网关已按路径校验 realm，此处再校验一次防内网直连绕过
            throw new ForbiddenException(ErrorCodeEnum.CROSS_REALM_FORBIDDEN, "无权访问该端接口");
        }
        webAuthService.changePassword(userId, dto);
        return R.ok();
    }

    /** 手机号脱敏：138****8000 */
    private String maskPhone(String phone) {
        if (phone == null) {
            return "***";
        }
        return phone.replaceAll(PHONE_MASK_REGEX, PHONE_MASK_REPLACEMENT);
    }
}
