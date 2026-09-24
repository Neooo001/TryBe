package com.trybe.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.trybe.common.auth.CaptchaStore;
import com.trybe.common.auth.SmsCodeFacade;
import com.trybe.common.auth.SmsScene;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BizIllegalException;
import com.trybe.common.exception.UnauthorizedException;
import com.trybe.common.service.OssService;
import com.trybe.user.domain.dto.BindPhoneDTO;
import com.trybe.user.domain.dto.PasswordLoginDTO;
import com.trybe.user.domain.dto.SmsLoginDTO;
import com.trybe.user.domain.dto.SmsSendDTO;
import com.trybe.user.domain.po.User;
import com.trybe.user.domain.vo.CaptchaVO;
import com.trybe.user.domain.vo.CreatorLoginVO;
import com.trybe.user.domain.vo.PhoneBindVO;
import com.trybe.user.enums.PhoneState;
import com.trybe.user.enums.UserStatus;
import com.trybe.user.mapper.UserMapper;
import com.trybe.user.service.AuthFacadeService;
import com.trybe.user.service.UserIdentityService;
import com.trybe.user.support.TokenIssuer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 统一账号体系认证门面实现。
 *
 * <p><b>事务边界</b>：本实现方法均含外部交互（短信 / 图形码 Redis），
 * <b>一律不加 {@code @Transactional}</b>；纯 DB 的归属转移/注册由
 * {@link UserIdentityService} 的 {@code @Transactional} 方法承担。
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthFacadeServiceImpl implements AuthFacadeService {

    private static final String ROLE_USER = "USER";
    private static final String CREDENTIAL_ERROR_MSG = "手机号或密码错误";

    private static final String PHONE_MASK_REGEX = "(\\d{3})\\d{4}(\\d{4})";
    private static final String PHONE_MASK_REPLACEMENT = "$1****$2";

    private final SmsCodeFacade smsCodeFacade;
    private final CaptchaStore captchaStore;
    private final UserIdentityService userIdentityService;
    private final TokenIssuer tokenIssuer;
    private final UserMapper userMapper;
    private final OssService ossService;

    // ==================== A1 图形验证码 ====================

    @Override
    public CaptchaVO generateCaptcha(String phone) {
        CaptchaStore.CaptchaImage image = captchaStore.create(phone);
        return new CaptchaVO(image.getCaptchaId(), image.getImageBase64(), CaptchaStore.TTL_SECONDS);
    }

    // ==================== A2 发送短信验证码 ====================

    @Override
    public void sendSmsCode(SmsSendDTO dto, String clientIp) {
        String phone = dto.getPhone().trim();
        SmsScene scene = SmsScene.of(dto.getScene());
        smsCodeFacade.sendCode(phone, dto.getCaptchaId(), dto.getCaptchaCode(), clientIp, scene);
    }

    // ==================== A3 短信验证码登录 ====================

    @Override
    public CreatorLoginVO loginBySms(SmsLoginDTO dto, String clientIp) {
        String phone = dto.getPhone().trim();

        if (!smsCodeFacade.verifySmsCode(phone, dto.getCode())) {
            log.warn("[auth] 短信登录失败：验证码错误 phone={} ip={}", maskPhone(phone), clientIp);
            throw new BizIllegalException(ErrorCodeEnum.BIZ_ERROR, "短信验证码错误或已失效");
        }

        Long userId = userIdentityService.resolveCurrentPhoneOwner(phone);
        if (userId == null) {
            // D2：手机号未注册 → 自动注册
            userId = autoRegisterByPhone(phone);
            log.info("[auth] 短信登录触发自动注册 userId={} phone={}", userId, maskPhone(phone));
        }

        User user = requireExistUser(userId);
        assertNotFrozen(user);

        TokenIssuer.TokenPair token = tokenIssuer.issue(userId, null);
        log.info("[auth] 短信登录成功 userId={} phone={}", userId, maskPhone(phone));
        return buildLoginVO(user, token, user.getPhoneState());
    }

    // ==================== A4 密码登录 ====================

    @Override
    public CreatorLoginVO loginByPassword(PasswordLoginDTO dto, String clientIp) {
        String phone = dto.getPhone().trim();

        // 图形验证码前置校验（P0-5）
        smsCodeFacade.verifyCaptcha(dto.getCaptchaId(), dto.getCaptchaCode(), phone);

        Long matchedUserId = userIdentityService.resolveUserIdByPhonePassword(phone, dto.getPassword());
        if (matchedUserId == null) {
            // 统一文案，防账号枚举
            log.warn("[auth] 密码登录失败：凭证不匹配 phone={} ip={}", maskPhone(phone), clientIp);
            throw new UnauthorizedException(ErrorCodeEnum.AUTH_FAILED, CREDENTIAL_ERROR_MSG);
        }

        User user = requireExistUser(matchedUserId);
        assertNotFrozen(user);

        // 登录成功后消费图形码（一次性）
        smsCodeFacade.consumeCaptcha(dto.getCaptchaId());

        // 恢复匹配判定：命中 ≠ 当前持有者 → recovery（说明是用「旧号 + 密码」走恢复路径登录）
        Long currentOwner = userIdentityService.resolveCurrentPhoneOwner(phone);
        boolean recovery = currentOwner == null || !currentOwner.equals(matchedUserId);
        // phoneState 由「该账号当前是否真的持有手机号」推导，而非由 recovery 直接决定：
        // 被释放手机号 P 的 A 若已自行补绑新号 Q（A.phone_state=BOUND），再用旧号 P + 密码登录时
        // recovery=true，但 A 显然已持有手机号 —— 若仍返回 RELEASED 会让前端对一个已绑号的人误弹补绑提示。
        String matchedCurrentPhone = userIdentityService.getCurrentPhone(matchedUserId);
        PhoneState phoneState = StrUtil.isNotBlank(matchedCurrentPhone)
                ? PhoneState.BOUND
                : (recovery ? PhoneState.RELEASED : user.getPhoneState());

        TokenIssuer.TokenPair token = tokenIssuer.issue(matchedUserId, null);
        log.info("[auth] 密码登录成功 userId={} phone={} recovery={}", matchedUserId, maskPhone(phone), recovery);
        return buildLoginVO(user, token, phoneState);
    }

    // ==================== A5 绑定手机号 ====================

    @Override
    public PhoneBindVO bindPhone(Long userId, BindPhoneDTO dto) {
        String phone = dto.getPhone().trim();

        if (!smsCodeFacade.verifySmsCode(phone, dto.getCode())) {
            throw new BizIllegalException(ErrorCodeEnum.BIZ_ERROR, "验证码错误或已过期");
        }

        userIdentityService.bindOrTransferInTx(userId, phone);
        log.info("[auth] 手机号绑定成功 userId={} phone={}", userId, maskPhone(phone));
        return new PhoneBindVO(userId, phone, PhoneState.BOUND);
    }

    // ==================== A6 查询当前手机号 ====================

    @Override
    public PhoneBindVO getPhone(Long userId) {
        User user = requireExistUser(userId);
        String phone = userIdentityService.getCurrentPhone(userId);
        return new PhoneBindVO(userId, phone, user.getPhoneState());
    }

    // ==================== 私有方法 ====================

    /**
     * 手机号未注册时自动注册（D2）；并发注册由唯一索引兜底，撞键后重新解析持有者。
     */
    private Long autoRegisterByPhone(String phone) {
        try {
            return userIdentityService.registerPhoneUserInTx(phone, ROLE_USER, null, null);
        } catch (DuplicateKeyException e) {
            Long existing = userIdentityService.resolveCurrentPhoneOwner(phone);
            if (existing == null) {
                throw new BizIllegalException(ErrorCodeEnum.PHONE_OCCUPIED, "操作冲突，请稍后重试");
            }
            log.info("[auth] 并发注册冲突，收敛到既有账号 userId={}", existing);
            return existing;
        }
    }

    private User requireExistUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizIllegalException(ErrorCodeEnum.DATA_NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private void assertNotFrozen(User user) {
        if (user.getStatus() == UserStatus.FROZEN) {
            throw new UnauthorizedException(ErrorCodeEnum.ACCOUNT_FROZEN, "账号异常，请联系客服");
        }
    }

    /**
     * 组装登录返回（含双 Token 与 phoneState）。phone 取用户「当前」手机号：
     * 恢复态账号（已被释放）此处为空，与设计 §A.4.4 一致。
     */
    private CreatorLoginVO buildLoginVO(User user, TokenIssuer.TokenPair token, PhoneState phoneState) {
        CreatorLoginVO vo = new CreatorLoginVO();
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setRole(user.getRole());
        vo.setPhone(userIdentityService.getCurrentPhone(user.getId()));
        vo.setPhoneState(phoneState);
        vo.setAccessToken(token.getAccessToken());
        vo.setRefreshToken(token.getRefreshToken());
        vo.setExpiresIn(token.getExpiresIn());
        if (StrUtil.isNotBlank(user.getAvatar())) {
            vo.setAvatar(ossService.getPublicUrl(user.getAvatar()));
        }
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
