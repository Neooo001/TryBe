package com.trybe.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.trybe.common.auth.CaptchaStore;
import com.trybe.common.auth.RealmConstants;
import com.trybe.common.auth.SmsCodeFacade;
import com.trybe.common.auth.SmsScene;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BizIllegalException;
import com.trybe.common.exception.UnauthorizedException;
import com.trybe.user.domain.dto.SmsSendDTO;
import com.trybe.user.domain.dto.WebLoginDTO;
import com.trybe.user.domain.dto.WebPasswordChangeDTO;
import com.trybe.user.domain.dto.WebRegisterDTO;
import com.trybe.user.domain.dto.WebSetPasswordDTO;
import com.trybe.user.domain.dto.WebSmsLoginDTO;
import com.trybe.user.domain.po.User;
import com.trybe.user.domain.po.UserPassword;
import com.trybe.user.domain.vo.CaptchaVO;
import com.trybe.user.domain.vo.WebLoginVO;
import com.trybe.user.enums.RegisterSource;
import com.trybe.user.enums.UserStatus;
import com.trybe.user.mapper.UserMapper;
import com.trybe.user.mapper.UserPasswordMapper;
import com.trybe.user.service.AuthService;
import com.trybe.user.service.UserIdentityService;
import com.trybe.user.service.WebAuthService;
import com.trybe.user.support.TokenIssuer;
import com.trybe.user.util.NicknameGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Web 端认证门面实现 —— {@code /web/auth/**}（账号体系 v3）
 *
 * <p>不再读写 {@code web_user}：账号统一在 {@code app_user}，
 * 手机号是唯一锚点，「注册」本质是按手机号<b>取或建</b>。
 *
 * <p><b>不加 {@code @Transactional}</b>：图形码校验与令牌签发均走 Redis，
 * 属外部交互；纯 DB 的建号/绑号下沉到 {@link UserIdentityService}。
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebAuthServiceImpl implements WebAuthService {

    private static final String CREDENTIAL_ERROR_MSG = "手机号或密码错误";
    private static final String NOT_REGISTERED_MSG = "该手机号未注册，请先注册";
    private static final String SMS_CODE_ERROR_MSG = "验证码错误或已过期";
    private static final String NEED_SET_PASSWORD_MSG = "该账号尚未设置密码，请先设置密码或用短信验证码登录";

    private static final String PHONE_MASK_REGEX = "(\\d{3})\\d{4}(\\d{4})";
    private static final String PHONE_MASK_REPLACEMENT = "$1****$2";

    private final SmsCodeFacade smsCodeFacade;
    private final CaptchaStore captchaStore;
    private final UserIdentityService userIdentityService;
    private final UserMapper userMapper;
    private final UserPasswordMapper userPasswordMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final AuthService authService;

    // ==================== 图形验证码 / 短信验证码 ====================

    @Override
    public CaptchaVO generateCaptcha(String phone) {
        CaptchaStore.CaptchaImage image = captchaStore.create(phone);
        return new CaptchaVO(image.getCaptchaId(), image.getImageBase64(), CaptchaStore.TTL_SECONDS);
    }

    @Override
    public void sendSmsCode(SmsSendDTO dto, String clientIp) {
        SmsScene scene = SmsScene.of(dto.getScene());
        smsCodeFacade.sendCode(dto.getPhone().trim(), dto.getCaptchaId(), dto.getCaptchaCode(), clientIp, scene);
    }

    @Override
    public void sendSetPasswordCode(Long userId, String clientIp) {
        String phone = userIdentityService.getCurrentPhone(userId);
        if (StrUtil.isBlank(phone)) {
            throw new BizIllegalException(ErrorCodeEnum.NEED_BIND_PHONE, "请先绑定手机号后再操作");
        }
        smsCodeFacade.sendCodeAuthed(phone, userId, clientIp, SmsScene.SET_PASSWORD);
    }

    // ==================== 注册（按手机号取或建） ====================

    @Override
    public WebLoginVO register(WebRegisterDTO dto, String clientIp) {
        String phone = dto.getPhone().trim();
        if (!smsCodeFacade.verifySmsCode(phone, dto.getCode())) {
            throw new BizIllegalException(ErrorCodeEnum.BIZ_ERROR, SMS_CODE_ERROR_MSG);
        }

        // 昵称：用户没填就随机生成（已存在账号时该值被忽略，沿用原昵称）
        String nickname = StrUtil.isBlank(dto.getNickname())
                ? NicknameGenerator.next()
                : dto.getNickname().trim();

        UserIdentityService.EnsureResult ensured =
                userIdentityService.ensureByPhoneInTx(phone, RegisterSource.WEB, nickname);

        User user = requireUser(ensured.getUserId());
        assertNotFrozen(user);
        userIdentityService.touchLogin(user.getId());

        log.info("[webAuth] 注册{} userId={} phone={} ip={}",
                ensured.isCreated() ? "新建" : "沿用已有账号",
                user.getId(), maskPhone(phone), clientIp);
        return buildVO(user, tokenIssuer.issue(user.getId(), RealmConstants.WEB, null), ensured.isCreated());
    }

    // ==================== 短信验证码登录 ====================

    @Override
    public WebLoginVO loginBySms(WebSmsLoginDTO dto, String clientIp) {
        String phone = dto.getPhone().trim();
        if (!smsCodeFacade.verifySmsCode(phone, dto.getCode())) {
            throw new BizIllegalException(ErrorCodeEnum.BIZ_ERROR, SMS_CODE_ERROR_MSG);
        }

        Long ownerId = userIdentityService.resolveCurrentPhoneOwner(phone);
        if (ownerId == null) {
            log.warn("[webAuth] 短信登录失败：手机号未注册 phone={} ip={}", maskPhone(phone), clientIp);
            throw new BizIllegalException(ErrorCodeEnum.DATA_NOT_FOUND, NOT_REGISTERED_MSG);
        }

        User user = requireUser(ownerId);
        assertNotFrozen(user);
        userIdentityService.touchLogin(user.getId());

        log.info("[webAuth] 短信登录成功 userId={} phone={}", user.getId(), maskPhone(phone));
        return buildVO(user, tokenIssuer.issue(user.getId(), RealmConstants.WEB, null), false);
    }

    // ==================== 密码登录 ====================

    @Override
    public WebLoginVO login(WebLoginDTO dto, String clientIp) {
        String phone = dto.getPhone().trim();

        // 校验并消费图形验证码（一次性：无论密码对错，图形码不可复用）
        smsCodeFacade.verifyAndConsumeCaptcha(dto.getCaptchaId(), dto.getCaptchaCode(), phone);

        Long matchedUserId = userIdentityService.resolveUserIdByPhonePassword(phone, dto.getPassword());
        if (matchedUserId == null) {
            if (userIdentityService.resolveCurrentPhoneOwner(phone) != null
                    && !userIdentityService.hasPassword(userIdentityService.resolveCurrentPhoneOwner(phone))) {
                // 已注册但从未设过密码（小程序先注册的用户很常见）→ 明确提示，不要笼统报「密码错误」
                throw new UnauthorizedException(ErrorCodeEnum.AUTH_FAILED, NEED_SET_PASSWORD_MSG);
            }
            // 统一文案，防账号枚举
            log.warn("[webAuth] 登录失败：凭证不匹配 phone={} ip={}", maskPhone(phone), clientIp);
            throw new UnauthorizedException(ErrorCodeEnum.AUTH_FAILED, CREDENTIAL_ERROR_MSG);
        }

        User user = requireUser(matchedUserId);
        assertNotFrozen(user);
        userIdentityService.touchLogin(user.getId());

        log.info("[webAuth] 密码登录成功 userId={} phone={}", user.getId(), maskPhone(phone));
        return buildVO(user, tokenIssuer.issue(user.getId(), RealmConstants.WEB, null), false);
    }

    // ==================== 设置 / 修改密码 ====================

    @Override
    public void setPassword(Long userId, WebSetPasswordDTO dto) {
        User user = requireUser(userId);
        assertNotFrozen(user);

        String phone = userIdentityService.getCurrentPhone(userId);
        if (StrUtil.isBlank(phone)) {
            throw new BizIllegalException(ErrorCodeEnum.NEED_BIND_PHONE, "请先绑定手机号后再操作");
        }
        if (!smsCodeFacade.verifySmsCode(phone, dto.getCode())) {
            throw new BizIllegalException(ErrorCodeEnum.BIZ_ERROR, SMS_CODE_ERROR_MSG);
        }

        upsertPassword(userId, passwordEncoder.encode(dto.getPassword()));
        log.info("[webAuth] 密码设置成功 userId={} phone={}", userId, maskPhone(phone));
    }

    @Override
    public void changePassword(Long userId, WebPasswordChangeDTO dto) {
        UserPassword existing = userPasswordMapper.selectById(userId);
        if (existing == null || StrUtil.isBlank(existing.getPasswordHash())) {
            throw new BizIllegalException(ErrorCodeEnum.BIZ_ERROR, NEED_SET_PASSWORD_MSG);
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), existing.getPasswordHash())) {
            throw new BizIllegalException(ErrorCodeEnum.BIZ_ERROR, "原密码不正确");
        }

        upsertPassword(userId, passwordEncoder.encode(dto.getNewPassword()));

        // 本域全部会话失效，强制重登（不触碰 wx 域）
        authService.logout(userId, null, RealmConstants.WEB);
        log.info("[webAuth] 密码修改成功 userId={}，本域会话已全部失效", userId);
    }

    // ==================== 私有方法 ====================

    /**
     * 密码 upsert：{@code user_password} 以 user_id 为主键，插入或覆盖同一条。
     */
    private void upsertPassword(Long userId, String passwordHash) {
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
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizIllegalException(ErrorCodeEnum.DATA_NOT_FOUND, "账号不存在");
        }
        return user;
    }

    private void assertNotFrozen(User user) {
        if (user.getStatus() != UserStatus.NORMAL) {
            throw new UnauthorizedException(ErrorCodeEnum.ACCOUNT_FROZEN, "账号状态异常，请联系客服");
        }
    }

    private WebLoginVO buildVO(User user, TokenIssuer.TokenPair token, boolean newUser) {
        WebLoginVO vo = new WebLoginVO();
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setPhone(maskPhone(userIdentityService.getCurrentPhone(user.getId())));
        vo.setHasPassword(userIdentityService.hasPassword(user.getId()));
        vo.setNewUser(newUser);
        vo.setRegisterSource(user.getRegisterSource() == null ? null : user.getRegisterSource().getValue());
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
