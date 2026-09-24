package com.trybe.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.UnauthorizedException;
import com.trybe.common.service.OssService;
import com.trybe.user.domain.po.User;
import com.trybe.user.domain.vo.UserLoginVO;
import com.trybe.user.enums.UserStatus;
import com.trybe.user.mapper.UserMapper;
import com.trybe.user.service.IPhoneService;
import com.trybe.user.service.UserIdentityService;
import com.trybe.user.support.TokenIssuer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 手机号管理服务实现（兼容端点，统一账号体系 v2）。
 *
 * <p>改为走 {@link UserIdentityService}：手机号一律经 {@code user_identity(PHONE)} 解析，
 * 密码经 {@code user_password} 校验；令牌统一经 {@link TokenIssuer} 下发（修复 P5）。
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhoneServiceImpl extends ServiceImpl<UserMapper, User> implements IPhoneService {

    private static final String PHONE_MASK_REGEX = "(\\d{3})\\d{4}(\\d{4})";
    private static final String PHONE_MASK_REPLACEMENT = "$1****$2";
    private static final String CREDENTIAL_ERROR_MSG = "手机号或密码错误";

    private final UserIdentityService userIdentityService;
    private final TokenIssuer tokenIssuer;
    private final OssService ossService;

    @Override
    public UserLoginVO loginByPhone(String phone, String password) {
        String maskedPhone = maskPhone(phone);

        // 「当前持有者 → 历史持有者」优先级 + 密码校验（含恢复路径）
        Long userId = userIdentityService.resolveUserIdByPhonePassword(phone, password);
        if (userId == null) {
            log.warn("[loginByPhone] 登录失败：凭证不匹配 phone={}", maskedPhone);
            throw new UnauthorizedException(ErrorCodeEnum.AUTH_FAILED, CREDENTIAL_ERROR_MSG);
        }

        User user = getById(userId);
        if (user == null) {
            log.warn("[loginByPhone] 登录失败：用户不存在 userId={} phone={}", userId, maskedPhone);
            throw new UnauthorizedException(ErrorCodeEnum.AUTH_FAILED, CREDENTIAL_ERROR_MSG);
        }
        if (user.getStatus() == UserStatus.FROZEN) {
            log.warn("[loginByPhone] 账号已冻结 userId={} phone={}", userId, maskedPhone);
            throw new UnauthorizedException(ErrorCodeEnum.ACCOUNT_FROZEN, "账号异常，请联系客服");
        }

        TokenIssuer.TokenPair token = tokenIssuer.issue(userId, null);

        UserLoginVO vo = new UserLoginVO();
        vo.setUserId(userId);
        vo.setNickname(user.getNickname());
        vo.setBio(user.getBio());
        vo.setGender(user.getGender());
        vo.setRole(user.getRole());
        vo.setPhone(userIdentityService.getCurrentPhone(userId));
        vo.setPhoneState(user.getPhoneState());
        vo.setAccessToken(token.getAccessToken());
        vo.setRefreshToken(token.getRefreshToken());
        vo.setExpiresIn(token.getExpiresIn());

        if (StrUtil.isNotBlank(user.getAvatar())) {
            vo.setAvatar(ossService.getPublicUrl(user.getAvatar()));
        }

        log.info("[loginByPhone] 登录成功 userId={} phone={} expiresIn={}s",
                userId, maskedPhone, token.getExpiresIn());
        return vo;
    }

    @Override
    public void bind(Long userId, String phone) {
        // 委托事务方法：空闲则绑定，被占用则归属转移
        userIdentityService.bindOrTransferInTx(userId, phone);
        log.info("[bindPhone] 手机号绑定成功 userId={} phone={}", userId, maskPhone(phone));
    }

    @Override
    public boolean isPhoneBound(String phone) {
        return userIdentityService.resolveCurrentPhoneOwner(phone) != null;
    }

    /** 手机号脱敏：138****8000 */
    private String maskPhone(String phone) {
        if (StrUtil.isBlank(phone)) {
            return "***";
        }
        return phone.replaceAll(PHONE_MASK_REGEX, PHONE_MASK_REPLACEMENT);
    }
}
