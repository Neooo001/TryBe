package com.trybe.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BizIllegalException;
import com.trybe.common.exception.UnauthorizedException;
import com.trybe.common.service.OssService;
import com.trybe.common.utils.BeanUtils;
import com.trybe.user.config.JwtProperties;
import com.trybe.user.domain.po.User;
import com.trybe.user.domain.vo.UserLoginVO;
import com.trybe.user.enums.UserStatus;
import com.trybe.user.mapper.UserMapper;
import com.trybe.user.service.IPhoneService;
import com.trybe.user.util.JwtTool;
import com.trybe.user.util.RefreshTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 手机号管理服务实现
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhoneServiceImpl extends ServiceImpl<UserMapper, User> implements IPhoneService {

    private static final String PHONE_MASK_REGEX = "(\\d{3})\\d{4}(\\d{4})";
    private static final String PHONE_MASK_REPLACEMENT = "$1****$2";

    private final JwtTool jwtTool;
    private final JwtProperties jwtProperties;
    private final RefreshTokenUtil refreshTokenUtil;
    private final OssService ossService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserLoginVO loginByPhone(String phone, String password) {
        User user = lambdaQuery().eq(User::getPhone, phone).one();
        String maskedPhone = phone.replaceAll(PHONE_MASK_REGEX, PHONE_MASK_REPLACEMENT);

        if (user == null) {
            log.warn("[loginByPhone] 手机号未注册 phone={}", maskedPhone);
            throw new UnauthorizedException(ErrorCodeEnum.AUTH_FAILED, "手机号或密码错误");
        }

        if (StrUtil.isBlank(user.getPassword())) {
            log.warn("[loginByPhone] 用户未设置密码 userId={} phone={}", user.getId(), maskedPhone);
            throw new UnauthorizedException(ErrorCodeEnum.AUTH_FAILED, "手机号或密码错误");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("[loginByPhone] 密码错误 userId={} phone={}", user.getId(), maskedPhone);
            throw new UnauthorizedException(ErrorCodeEnum.AUTH_FAILED, "手机号或密码错误");
        }

        if (user.getStatus() == UserStatus.FROZEN) {
            log.warn("[loginByPhone] 账号已冻结 userId={} phone={}", user.getId(), maskedPhone);
            throw new UnauthorizedException(ErrorCodeEnum.ACCOUNT_FROZEN, "账号异常，请联系客服");
        }

        Duration accessTtl = jwtProperties.getAccessTokenTTL();
        String accessToken = jwtTool.createToken(user.getId(), accessTtl);
        String refreshToken = refreshTokenUtil.generateToken();
        refreshTokenUtil.saveToken(refreshToken, user.getId(), user.getOpenid());

        log.info("[loginByPhone] 登录成功 userId={} phone={} accessTokenExpiresIn={}s",
                user.getId(), maskedPhone, accessTtl.getSeconds());

        UserLoginVO vo = BeanUtils.copyBean(user, UserLoginVO.class);
        vo.setAccessToken(accessToken);
        vo.setRefreshToken(refreshToken);
        vo.setExpiresIn(accessTtl.getSeconds());

        if (StrUtil.isNotBlank(user.getAvatar())) {
            vo.setAvatar(ossService.getPublicUrl(user.getAvatar()));
        }

        return vo;
    }

    @Override
    @Transactional
    public void bind(Long userId, String phone) {
        User user = getById(userId);
        if (user == null) {
            throw new BizIllegalException(ErrorCodeEnum.DATA_NOT_FOUND, "用户不存在");
        }

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