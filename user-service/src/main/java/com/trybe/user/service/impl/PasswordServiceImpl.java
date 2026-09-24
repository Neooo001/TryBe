package com.trybe.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BizIllegalException;
import com.trybe.common.service.SmsService;
import com.trybe.user.domain.po.UserPassword;
import com.trybe.user.mapper.UserPasswordMapper;
import com.trybe.user.service.IPasswordService;
import com.trybe.user.service.UserIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 密码管理服务实现（统一账号体系 v2）。
 *
 * <p>密码改存 {@code user_password}（按 user_id），与手机号解耦：
 * 手机号易主后密码仍随「人」走。当前手机号从 {@code user_identity(PHONE)} 取。
 *
 * <p><b>事务</b>：本方法含外部交互（短信校验），<b>不加 {@code @Transactional}</b>；
 * 密码写入为单条 upsert（主键 user_id 保证唯一），无需跨语句事务。
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordServiceImpl implements IPasswordService {

    private static final String PHONE_MASK_REGEX = "(\\d{3})\\d{4}(\\d{4})";
    private static final String PHONE_MASK_REPLACEMENT = "$1****$2";

    private final SmsService smsService;
    private final PasswordEncoder passwordEncoder;
    private final UserIdentityService userIdentityService;
    private final UserPasswordMapper userPasswordMapper;

    @Override
    public void setPassword(Long userId, String code, String password) {
        String phone = userIdentityService.getCurrentPhone(userId);
        if (StrUtil.isBlank(phone)) {
            // 未绑号账号调用受保护接口的服务端兜底
            throw new BizIllegalException(ErrorCodeEnum.NEED_BIND_PHONE, "请先绑定手机号");
        }

        if (!smsService.verifyCode(phone, code)) {
            throw new BizIllegalException(ErrorCodeEnum.BIZ_ERROR, "验证码错误或已过期");
        }

        String passwordHash = passwordEncoder.encode(password);
        UserPassword existing = userPasswordMapper.selectById(userId);
        if (existing == null) {
            userPasswordMapper.insert(new UserPassword()
                    .setUserId(userId)
                    .setPasswordHash(passwordHash));
        } else {
            userPasswordMapper.updateById(new UserPassword()
                    .setUserId(userId)
                    .setPasswordHash(passwordHash));
        }

        log.info("[setPassword] 密码设置成功 userId={} phone={}", userId, maskPhone(phone));
    }

    /** 手机号脱敏：138****8000 */
    private String maskPhone(String phone) {
        if (StrUtil.isBlank(phone)) {
            return "***";
        }
        return phone.replaceAll(PHONE_MASK_REGEX, PHONE_MASK_REPLACEMENT);
    }
}
