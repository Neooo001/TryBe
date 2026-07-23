package com.trybe.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BizIllegalException;
import com.trybe.user.domain.po.User;
import com.trybe.user.mapper.UserMapper;
import com.trybe.user.service.IPhoneService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 手机号管理服务实现
 *
 * @author TryBe Team
 */
@Slf4j
@Service
public class PhoneServiceImpl extends ServiceImpl<UserMapper, User> implements IPhoneService {

    /** 手机号脱敏正则：138****8888 */
    private static final String PHONE_MASK_REGEX = "(\\d{3})\\d{4}(\\d{4})";
    private static final String PHONE_MASK_REPLACEMENT = "$1****$2";

    @Override
    @Transactional
    public void bind(Long userId, String phone) {
        User user = getById(userId);
        if (user == null) {
            throw new BizIllegalException(ErrorCodeEnum.DATA_NOT_FOUND, "用户不存在");
        }

        // 检查手机号是否已被其他用户绑定
        User existUser = lambdaQuery()
                .eq(User::getPhone, phone)
                .ne(User::getId, userId)
                .one();
        if (existUser != null) {
            throw new BizIllegalException(ErrorCodeEnum.BIZ_ERROR, "该手机号已被其他账号绑定");
        }

        user.setPhone(phone);
        updateById(user);

        String maskedPhone = phone.replaceAll(PHONE_MASK_REGEX, PHONE_MASK_REPLACEMENT);
        log.info("[bindPhone] 手机号绑定成功 userId={} phone={}", userId, maskedPhone);
    }

    @Override
    public boolean isPhoneBound(String phone) {
        return lambdaQuery().eq(User::getPhone, phone).count() > 0;
    }
}
