package com.trybe.user.controller;

import com.trybe.common.auth.SmsRateLimiter;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.domain.R;
import com.trybe.common.utils.UserContext;
import com.trybe.user.domain.dto.BindPhoneDTO;
import com.trybe.user.domain.dto.PhoneLoginDTO;
import com.trybe.user.domain.dto.SendCodeDTO;
import com.trybe.user.domain.vo.UserLoginVO;
import com.trybe.user.service.IPhoneService;
import com.trybe.common.service.SmsService;
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
 * 手机号管理控制器
 *
 * <p>手机号密码登录无需 AccessToken 鉴权；绑定手机号是登录后的附加操作，需要鉴权。
 *
 * @author TryBe Team
 */
@Api(tags = "手机号管理接口")
@RestController
@RequestMapping("/users/phone")
@RequiredArgsConstructor
@Slf4j
public class PhoneController {

    private final SmsService smsService;
    private final IPhoneService phoneService;
    private final SmsRateLimiter smsRateLimiter;

    @ApiOperation("手机号密码登录")
    @PostMapping("login")
    public R<UserLoginVO> loginByPhone(@RequestBody @Validated PhoneLoginDTO dto) {
        log.info("手机号密码登录 phone={}", dto.getPhone().replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
        return R.ok(phoneService.loginByPhone(dto.getPhone(), dto.getPassword()));
    }

    @ApiOperation("发送短信验证码")
    @PostMapping("send-code")
    public R<Void> sendVerifyCode(@RequestBody @Validated SendCodeDTO dto, HttpServletRequest request) {
        // legacy 端点保留兼容（不破坏请求契约、不加图形码），但必须复用统一的三级日限流，
        // 否则它将成为一个绕过 SmsRateLimiter（手机号 10/天、IP 30/天、全局 200/天）的不限量短信入口。
        String clientIp = SmsRateLimiter.normalizeIp(request.getHeader("X-Forwarded-For"));
        smsRateLimiter.check(dto.getPhone(), clientIp);
        smsService.sendVerifyCode(dto.getPhone());
        return R.ok();
    }

    @ApiOperation("绑定手机号")
    @PostMapping("bind")
    public R<Void> bindPhone(@RequestBody @Validated BindPhoneDTO dto) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            return R.error(ErrorCodeEnum.NOT_LOGIN, "请先登录");
        }
        log.info("绑定手机号 userId={}", userId);

        if (!smsService.verifyCode(dto.getPhone(), dto.getCode())) {
            return R.error(ErrorCodeEnum.BIZ_ERROR, "验证码错误或已过期");
        }

        phoneService.bind(userId, dto.getPhone());
        return R.ok();
    }
}