package com.trybe.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.trybe.common.auth.CaptchaStore;
import com.trybe.common.auth.RealmConstants;
import com.trybe.common.auth.SmsCodeFacade;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.UnauthorizedException;
import com.trybe.user.domain.dto.WebLoginDTO;
import com.trybe.user.domain.dto.WebPasswordChangeDTO;
import com.trybe.user.domain.dto.WebRegisterDTO;
import com.trybe.user.domain.po.WebUser;
import com.trybe.user.domain.vo.CaptchaVO;
import com.trybe.user.domain.vo.WebLoginVO;
import com.trybe.user.enums.UserStatus;
import com.trybe.user.mapper.WebUserMapper;
import com.trybe.user.service.AuthService;
import com.trybe.user.service.WebAuthService;
import com.trybe.user.service.WebUserTxService;
import com.trybe.user.support.TokenIssuer;
import com.trybe.user.util.RefreshTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Web 域认证门面实现 —— {@code /web/auth/**}
 *
 * <p><b>不加 {@code @Transactional}</b>：图形码校验与令牌签发均走 Redis，
 * 属外部交互；纯 DB 的注册/改密下沉到 {@link WebUserTxService}。
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebAuthServiceImpl implements WebAuthService {

    private static final String CREDENTIAL_ERROR_MSG = "手机号或密码错误";

    private static final String PHONE_MASK_REGEX = "(\\d{3})\\d{4}(\\d{4})";
    private static final String PHONE_MASK_REPLACEMENT = "$1****$2";

    private final SmsCodeFacade smsCodeFacade;
    private final CaptchaStore captchaStore;
    private final WebUserTxService webUserTxService;
    private final WebUserMapper webUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final RefreshTokenUtil refreshTokenUtil;
    private final AuthService authService;

    @Override
    public CaptchaVO generateCaptcha(String phone) {
        CaptchaStore.CaptchaImage image = captchaStore.create(phone);
        return new CaptchaVO(image.getCaptchaId(), image.getImageBase64(), CaptchaStore.TTL_SECONDS);
    }

    @Override
    public WebLoginVO register(WebRegisterDTO dto, String clientIp) {
        String phone = dto.getPhone().trim();

        // 校验并消费图形验证码（一次性：Web 注册无短信验证，此为唯一人机防护）
        smsCodeFacade.verifyAndConsumeCaptcha(dto.getCaptchaId(), dto.getCaptchaCode(), phone);

        WebUser user = webUserTxService.registerInTx(dto);

        log.info("[webAuth] 注册成功 userId={} phone={} ip={}", user.getId(), maskPhone(phone), clientIp);
        return buildLoginVO(user, tokenIssuer.issue(user.getId(), RealmConstants.WEB, null));
    }

    @Override
    public WebLoginVO login(WebLoginDTO dto, String clientIp) {
        String phone = dto.getPhone().trim();

        // 校验并消费图形验证码（一次性：无论密码对错，图形码不可复用）
        smsCodeFacade.verifyAndConsumeCaptcha(dto.getCaptchaId(), dto.getCaptchaCode(), phone);

        WebUser user = webUserMapper.selectByPhone(phone);
        if (user == null || StrUtil.isBlank(user.getPasswordHash())
                || !passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            // 统一文案，防账号枚举
            log.warn("[webAuth] 登录失败：凭证不匹配 phone={} ip={}", maskPhone(phone), clientIp);
            throw new UnauthorizedException(ErrorCodeEnum.AUTH_FAILED, CREDENTIAL_ERROR_MSG);
        }
        if (user.getStatus() == UserStatus.FROZEN) {
            throw new UnauthorizedException(ErrorCodeEnum.ACCOUNT_FROZEN, "账号异常，请联系客服");
        }
        webUserMapper.touchLogin(user.getId());

        log.info("[webAuth] 登录成功 userId={} phone={}", user.getId(), maskPhone(phone));
        return buildLoginVO(user, tokenIssuer.issue(user.getId(), RealmConstants.WEB, null));
    }

    @Override
    public void changePassword(Long userId, WebPasswordChangeDTO dto) {
        WebUser user = webUserMapper.selectById(userId);
        if (user == null) {
            throw new UnauthorizedException(ErrorCodeEnum.NOT_LOGIN, "账号不存在，请重新登录");
        }
        if (StrUtil.isBlank(user.getPasswordHash())
                || !passwordEncoder.matches(dto.getOldPassword(), user.getPasswordHash())) {
            throw new com.trybe.common.exception.BizIllegalException(
                    ErrorCodeEnum.BIZ_ERROR, "原密码不正确");
        }

        String newHash = passwordEncoder.encode(dto.getNewPassword());
        webUserTxService.changePasswordHashInTx(userId, newHash);

        // 本域全部会话失效，强制重登（不触碰 wx 域）
        authService.logout(userId, null, RealmConstants.WEB);

        log.info("[webAuth] 密码修改成功 userId={}，本域会话已全部失效", userId);
    }

    private WebLoginVO buildLoginVO(WebUser user, TokenIssuer.TokenPair token) {
        WebLoginVO vo = new WebLoginVO();
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAccessToken(token.getAccessToken());
        vo.setRefreshToken(token.getRefreshToken());
        vo.setExpiresIn(token.getExpiresIn());
        return vo;
    }

    /** 手机号脱敏：138****8000 */
    private String maskPhone(String phone) {
        if (StrUtil.isBlank(phone)) {
            return "***";
        }
        return phone.replaceAll(PHONE_MASK_REGEX, PHONE_MASK_REPLACEMENT);
    }
}