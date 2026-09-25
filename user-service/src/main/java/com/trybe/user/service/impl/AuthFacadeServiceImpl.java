package com.trybe.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.trybe.common.auth.CaptchaStore;
import com.trybe.common.auth.RealmConstants;
import com.trybe.common.auth.SmsCodeFacade;
import com.trybe.common.auth.SmsScene;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BizIllegalException;
import com.trybe.common.exception.UnauthorizedException;
import com.trybe.common.service.OssService;
import com.trybe.user.domain.dto.BindPhoneDTO;
import com.trybe.user.domain.dto.PasswordLoginDTO;
import com.trybe.user.domain.dto.SmsSendAuthedDTO;
import com.trybe.user.domain.dto.SmsSendDTO;
import com.trybe.user.domain.po.User;
import com.trybe.user.domain.vo.CaptchaVO;
import com.trybe.user.domain.vo.PhoneBindVO;
import com.trybe.user.domain.vo.UserLoginVO;
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

import java.util.regex.Pattern;

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

    private static final String CREDENTIAL_ERROR_MSG = "手机号或密码错误";

    private static final String PHONE_MASK_REGEX = "(\\d{3})\\d{4}(\\d{4})";
    private static final String PHONE_MASK_REPLACEMENT = "$1****$2";

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

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

    // ==================== A2b 已登录用户发码（免图形码） ====================

    @Override
    public void sendSmsCodeForLoginUser(Long userId, SmsSendAuthedDTO dto, String clientIp) {
        SmsScene scene = SmsScene.of(dto.getScene());
        String phone;
        if (scene == SmsScene.SET_PASSWORD) {
            // 强凭据：只发到该账号当前绑定的号码，绝不接受请求体里的 phone。
            // 否则会出现「短信发到 A 号、但 /users/password/set 用库里的 B 号校验」的错位。
            phone = userIdentityService.getCurrentPhone(userId);
            if (StrUtil.isBlank(phone)) {
                throw new BizIllegalException(ErrorCodeEnum.NEED_BIND_PHONE, "请先绑定手机号后再操作");
            }
        } else {
            phone = dto.getPhone() == null ? null : dto.getPhone().trim();
            if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
                throw new BizIllegalException(ErrorCodeEnum.PARAM_ERROR, "请输入正确的手机号");
            }
        }
        smsCodeFacade.sendCodeAuthed(phone, userId, clientIp, scene);
    }

    // ==================== 密码登录（wx 域唯一登录方式） ====================

    @Override
    public UserLoginVO loginByPassword(PasswordLoginDTO dto, String clientIp) {
        String phone = dto.getPhone().trim();

        // 校验并消费图形验证码（一次性：无论密码对错，图形码不可复用）
        smsCodeFacade.verifyAndConsumeCaptcha(dto.getCaptchaId(), dto.getCaptchaCode(), phone);

        Long matchedUserId = userIdentityService.resolveUserIdByPhonePassword(phone, dto.getPassword());
        if (matchedUserId == null) {
            // 统一文案，防账号枚举
            log.warn("[auth] 密码登录失败：凭证不匹配 phone={} ip={}", maskPhone(phone), clientIp);
            throw new UnauthorizedException(ErrorCodeEnum.AUTH_FAILED, CREDENTIAL_ERROR_MSG);
        }

        User user = requireExistUser(matchedUserId);
        assertNotFrozen(user);

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

        TokenIssuer.TokenPair token = tokenIssuer.issue(matchedUserId, RealmConstants.WX, null);
        log.info("[auth] 密码登录成功(wx域) userId={} phone={} recovery={}", matchedUserId, maskPhone(phone), recovery);
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
    private UserLoginVO buildLoginVO(User user, TokenIssuer.TokenPair token, PhoneState phoneState) {
        UserLoginVO vo = new UserLoginVO();
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
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