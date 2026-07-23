package com.trybe.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BizIllegalException;
import com.trybe.common.service.SmsService;
import com.trybe.user.domain.po.User;
import com.trybe.user.mapper.UserMapper;
import com.trybe.user.service.IPasswordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 密码管理服务实现
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordServiceImpl extends ServiceImpl<UserMapper, User> implements IPasswordService {

    private final SmsService smsService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void setPassword(Long userId, String code, String password) {
        User user = getById(userId);
        if (user == null) {
            throw new BizIllegalException(ErrorCodeEnum.DATA_NOT_FOUND, "用户不存在");
        }

        if (StrUtil.isBlank(user.getPhone())) {
            throw new BizIllegalException(ErrorCodeEnum.BIZ_ERROR, "请先绑定手机号");
        }

        if (!smsService.verifyCode(user.getPhone(), code)) {
            throw new BizIllegalException(ErrorCodeEnum.BIZ_ERROR, "验证码错误或已过期");
        }

        user.setPassword(passwordEncoder.encode(password));
        user.setUpdateTime(LocalDateTime.now());
        updateById(user);

        log.info("[setPassword] 密码设置成功 userId={}", userId);
    }
}