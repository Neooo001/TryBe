package com.trybe.user.controller;

import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.domain.R;
import com.trybe.common.utils.UserContext;
import com.trybe.user.domain.dto.SetPasswordDTO;
import com.trybe.user.service.IPasswordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 密码管理控制器
 *
 * <p>设置/修改密码是登录后的安全操作，接口需要 AccessToken 鉴权。
 * 验证码发送复用 {@code POST /users/phone/send-code} 接口，
 * 验证码校验基于用户已绑定的手机号，由 Service 层自动获取。
 *
 * @author TryBe Team
 */
@Api(tags = "密码管理接口")
@RestController
@RequestMapping("/users/password")
@RequiredArgsConstructor
@Slf4j
public class PasswordController {

    private final IPasswordService passwordService;

    @ApiOperation("设置密码")
    @PostMapping("set")
    public R<Void> setPassword(@RequestBody @Validated SetPasswordDTO dto) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            return R.error(ErrorCodeEnum.NOT_LOGIN, "请先登录");
        }
        log.info("设置密码 userId={}", userId);
        passwordService.setPassword(userId, dto.getCode(), dto.getPassword());
        return R.ok();
    }
}