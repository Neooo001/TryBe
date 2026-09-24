package com.trybe.user.controller;

import com.trybe.common.auth.SmsRateLimiter;
import com.trybe.common.domain.R;
import com.trybe.common.utils.UserContext;
import com.trybe.user.domain.dto.CaptchaReqDTO;
import com.trybe.user.domain.dto.CreatorLoginDTO;
import com.trybe.user.domain.dto.CreatorRegisterDTO;
import com.trybe.user.domain.dto.CreatorSendCodeDTO;
import com.trybe.user.domain.vo.CaptchaVO;
import com.trybe.user.domain.vo.CreatorLoginVO;
import com.trybe.user.domain.vo.UserRoleVO;
import com.trybe.user.service.ICreatorAuthService;
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
 * 创作者端认证控制器
 *
 * <p>面向创作者 Web 后台，与小程序侧登录接口（{@code /users/phone/login}、
 * {@code /users/login}）完全隔离 —— 两端各自演进，互不复用 Controller / Service / DTO。
 *
 * <p><b>网关白名单（hm.auth.excludePaths）需要放行的端点</b>：
 * <ul>
 *   <li>{@code POST /users/creator/auth/register}</li>
 *   <li>{@code POST /users/creator/auth/login}</li>
 *   <li>{@code POST /users/creator/auth/captcha}</li>
 *   <li>{@code POST /users/creator/auth/send-code}</li>
 * </ul>
 * 而 {@code POST /users/creator/auth/upgrade} <b>必须登录</b>，由网关鉴权后写入
 * {@code user-info} 请求头，经 {@link UserContext} 取 userId —— 刻意不放行。
 *
 * @author TryBe Team
 */
@Api(tags = "创作者认证接口")
@RestController
@RequestMapping("/users/creator/auth")
@RequiredArgsConstructor
@Slf4j
public class CreatorAuthController {

    private static final String PHONE_MASK_REGEX = "(\\d{3})\\d{4}(\\d{4})";
    private static final String PHONE_MASK_REPLACEMENT = "$1****$2";

    private final ICreatorAuthService creatorAuthService;

    @ApiOperation("C1 创作者注册（注册即成为创作者，直接返回双 Token）")
    @PostMapping("/register")
    public R<CreatorLoginVO> register(@RequestBody @Validated CreatorRegisterDTO dto) {
        log.info("创作者注册 phone={}", dto.getPhone().replaceAll(PHONE_MASK_REGEX, PHONE_MASK_REPLACEMENT));
        return R.ok(creatorAuthService.register(dto));
    }

    @ApiOperation("C2 创作者登录（手机号密码，放行 USER，由前端按 role 引导升级）")
    @PostMapping("/login")
    public R<CreatorLoginVO> login(@RequestBody @Validated CreatorLoginDTO dto) {
        log.info("创作者登录 phone={}", dto.getPhone().replaceAll(PHONE_MASK_REGEX, PHONE_MASK_REPLACEMENT));
        return R.ok(creatorAuthService.login(dto));
    }

    @ApiOperation("C3 获取图形验证码（免鉴权，注册前置校验）")
    @PostMapping("/captcha")
    public R<CaptchaVO> captcha(@RequestBody(required = false) CaptchaReqDTO dto) {
        return R.ok(creatorAuthService.generateCaptcha(dto == null ? null : dto.getPhone()));
    }

    @ApiOperation("C4 发送注册短信验证码（免鉴权，图形码 + 三级限流防护）")
    @PostMapping("/send-code")
    public R<Void> sendCode(@RequestBody @Validated CreatorSendCodeDTO dto, HttpServletRequest request) {
        String clientIp = SmsRateLimiter.normalizeIp(request.getHeader("X-Forwarded-For"));
        creatorAuthService.sendRegisterCode(dto, clientIp);
        return R.ok();
    }

    @ApiOperation("C5 升级为创作者（需登录，USER → CREATOR）")
    @PostMapping("/upgrade")
    public R<UserRoleVO> upgrade() {
        Long userId = UserContext.requireUser();
        return R.ok(creatorAuthService.upgradeToCreator(userId));
    }
}
