package com.trybe.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.trybe.common.auth.CaptchaStore;
import com.trybe.common.auth.SmsCodeFacade;
import com.trybe.common.auth.SmsScene;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BizIllegalException;
import com.trybe.common.exception.UnauthorizedException;
import com.trybe.common.service.OssService;
import com.trybe.user.domain.dto.CreatorLoginDTO;
import com.trybe.user.domain.dto.CreatorRegisterDTO;
import com.trybe.user.domain.dto.CreatorSendCodeDTO;
import com.trybe.user.domain.po.User;
import com.trybe.user.domain.vo.CaptchaVO;
import com.trybe.user.domain.vo.CreatorLoginVO;
import com.trybe.user.domain.vo.UserRoleVO;
import com.trybe.user.enums.UserStatus;
import com.trybe.user.mapper.UserMapper;
import com.trybe.user.service.ICreatorAuthService;
import com.trybe.user.service.UserIdentityService;
import com.trybe.user.support.TokenIssuer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 创作者端认证服务实现（兼容端点，统一账号体系 v2）。
 *
 * <p>改造点：
 * <ul>
 *   <li>图形码/限流/发码统一委托 {@link SmsCodeFacade}（common），消除重复编排；</li>
 *   <li>手机号一律经 {@code user_identity(PHONE)} 解析；密码写入 {@code user_password}；</li>
 *   <li>令牌统一经 {@link TokenIssuer} 下发（修复 P5）。</li>
 * </ul>
 *
 * <p>注册语义：注册即成为创作者（{@code role=CREATOR}），直接下发双 Token。
 * 登录语义：放行 USER —— 共用账号体系下 USER 登录后台是正常路径。
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreatorAuthServiceImpl implements ICreatorAuthService {

    private static final String PHONE_MASK_REGEX = "(\\d{3})\\d{4}(\\d{4})";
    private static final String PHONE_MASK_REPLACEMENT = "$1****$2";

    private static final String ROLE_USER = "USER";
    private static final String ROLE_CREATOR = "CREATOR";
    private static final String ROLE_ADMIN = "ADMIN";

    /** creator-service 的 RoleGuard 角色缓存前缀，升级后必须清除，否则 30 分钟内仍按 USER 判定 */
    private static final String ROLE_CACHE_KEY_PREFIX = "creator:role:";

    /** 对外统一文案：不区分"手机号不存在"与"密码错误"，避免账号枚举 */
    private static final String CREDENTIAL_ERROR_MSG = "手机号或密码错误";

    private final UserMapper userMapper;
    private final UserIdentityService userIdentityService;
    private final TokenIssuer tokenIssuer;
    private final OssService ossService;
    private final SmsCodeFacade smsCodeFacade;
    private final CaptchaStore captchaStore;
    private final StringRedisTemplate stringRedisTemplate;

    // ==================== 注册 ====================

    /**
     * 创作者注册。
     *
     * <p>校验顺序：图形验证码 → 短信验证码 → 手机号唯一性 → 建号。
     * 图形验证码在注册成功后才真正消费（一次性）。
     *
     * <p>方法内含外部交互（短信校验），<b>不加 {@code @Transactional}</b>；
     * 建号（纯 DB）委托 {@link UserIdentityService#registerPhoneUserInTx}。
     */
    @Override
    public CreatorLoginVO register(CreatorRegisterDTO dto) {
        String phone = dto.getPhone().trim();

        // 图形验证码 → CAPTCHA_ERROR(105)
        smsCodeFacade.verifyCaptcha(dto.getCaptchaId(), dto.getCaptchaCode(), phone);

        // 短信验证码
        if (!smsCodeFacade.verifySmsCode(phone, dto.getCode())) {
            throw new BizIllegalException(ErrorCodeEnum.BIZ_ERROR, "短信验证码错误或已失效");
        }

        // 手机号唯一性（走 user_identity）
        Long existOwner = userIdentityService.resolveCurrentPhoneOwner(phone);
        if (existOwner != null) {
            User exist = userMapper.selectById(existOwner);
            String existRole = exist == null ? "" : StrUtil.trimToEmpty(exist.getRole());
            log.warn("[creatorAuth] 注册失败：手机号已存在 phone={} role={}", maskPhone(phone), existRole);
            if (ROLE_USER.equals(existRole)) {
                throw new BizIllegalException(
                        ErrorCodeEnum.BIZ_ERROR, "该手机号已注册，请直接登录并在后台申请成为创作者");
            }
            throw new BizIllegalException(ErrorCodeEnum.BIZ_ERROR, "该手机号已注册，请直接登录");
        }

        String nickname = StrUtil.isBlank(dto.getNickname()) ? null : dto.getNickname().trim();
        Long userId;
        try {
            userId = userIdentityService.registerPhoneUserInTx(phone, ROLE_CREATOR, nickname, dto.getPassword());
        } catch (DuplicateKeyException e) {
            // 并发注册兜底
            log.warn("[creatorAuth] 注册并发冲突 phone={}", maskPhone(phone));
            throw new BizIllegalException(ErrorCodeEnum.BIZ_ERROR, "该手机号已注册，请直接登录");
        }

        smsCodeFacade.consumeCaptcha(dto.getCaptchaId());

        User user = userMapper.selectById(userId);
        log.info("[creatorAuth] 创作者注册成功 userId={} phone={}", userId, maskPhone(phone));
        return buildLoginVO(user, phone);
    }

    // ==================== 登录 ====================

    /**
     * 创作者登录。
     *
     * <p>刻意不校验 role：共用账号体系下 USER 登录后台是正常路径，
     * 由前端根据 VO 中的 role 决定落地页（USER → /upgrade）。
     */
    @Override
    public CreatorLoginVO login(CreatorLoginDTO dto) {
        String phone = dto.getPhone().trim();
        String maskedPhone = maskPhone(phone);

        Long userId = userIdentityService.resolveUserIdByPhonePassword(phone, dto.getPassword());
        if (userId == null) {
            log.warn("[creatorAuth] 登录失败：凭证不匹配 phone={}", maskedPhone);
            throw new UnauthorizedException(ErrorCodeEnum.AUTH_FAILED, CREDENTIAL_ERROR_MSG);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("[creatorAuth] 登录失败：用户不存在 userId={}", userId);
            throw new UnauthorizedException(ErrorCodeEnum.AUTH_FAILED, CREDENTIAL_ERROR_MSG);
        }
        if (user.getStatus() == UserStatus.FROZEN) {
            log.warn("[creatorAuth] 登录失败：账号已冻结 userId={}", userId);
            throw new UnauthorizedException(ErrorCodeEnum.ACCOUNT_FROZEN, "账号异常，请联系客服");
        }

        log.info("[creatorAuth] 登录成功 userId={} role={}", userId, user.getRole());
        return buildLoginVO(user, phone);
    }

    // ==================== 图形验证码 ====================

    @Override
    public CaptchaVO generateCaptcha(String phone) {
        CaptchaStore.CaptchaImage image = captchaStore.create(phone);
        return new CaptchaVO(image.getCaptchaId(), image.getImageBase64(), CaptchaStore.TTL_SECONDS);
    }

    // ==================== 发送注册短信验证码 ====================

    @Override
    public void sendRegisterCode(CreatorSendCodeDTO dto, String clientIp) {
        String phone = dto.getPhone().trim();
        smsCodeFacade.sendCode(phone, dto.getCaptchaId(), dto.getCaptchaCode(), clientIp, SmsScene.REGISTER);
    }

    // ==================== 升级为创作者 ====================

    /**
     * USER → CREATOR。
     *
     * <p>幂等：已是 CREATOR / ADMIN 时直接返回当前角色。
     * 升级后必须删除 Redis 中的角色缓存，否则 creator-service 的 RoleGuard
     * 会在缓存期内继续把该用户判成 USER。
     */
    @Override
    @Transactional
    public UserRoleVO upgradeToCreator(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizIllegalException(ErrorCodeEnum.DATA_NOT_FOUND, "用户不存在");
        }
        if (user.getStatus() == UserStatus.FROZEN) {
            throw new BizIllegalException(ErrorCodeEnum.ACCOUNT_FROZEN, "账号异常，请联系客服");
        }

        String role = StrUtil.trimToEmpty(user.getRole());
        if (ROLE_CREATOR.equals(role) || ROLE_ADMIN.equals(role)) {
            return new UserRoleVO(userId, role);
        }
        if (StrUtil.isNotBlank(role) && !ROLE_USER.equals(role)) {
            throw new BizIllegalException(ErrorCodeEnum.BIZ_ERROR, "当前账号状态不支持升级");
        }

        userMapper.updateById(new User().setId(userId).setRole(ROLE_CREATOR));
        stringRedisTemplate.delete(ROLE_CACHE_KEY_PREFIX + userId);

        log.info("[creatorAuth] 用户升级为创作者 userId={}", userId);
        return new UserRoleVO(userId, ROLE_CREATOR);
    }

    // ==================== 私有方法 ====================

    /**
     * 组装登录返回（含双 Token 下发）。
     */
    private CreatorLoginVO buildLoginVO(User user, String phone) {
        TokenIssuer.TokenPair token = tokenIssuer.issue(user.getId(), null);

        CreatorLoginVO vo = new CreatorLoginVO();
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        // 手机号从 user_identity 取（user 表已无 phone 列）
        vo.setPhone(userIdentityService.getCurrentPhone(user.getId()));
        vo.setRole(user.getRole());
        vo.setPhoneState(user.getPhoneState());
        vo.setAccessToken(token.getAccessToken());
        vo.setRefreshToken(token.getRefreshToken());
        vo.setExpiresIn(token.getExpiresIn());

        if (StrUtil.isNotBlank(user.getAvatar())) {
            vo.setAvatar(ossService.getPublicUrl(user.getAvatar()));
        }
        return vo;
    }

    /**
     * 手机号脱敏：138****8000
     */
    private String maskPhone(String phone) {
        return phone.replaceAll(PHONE_MASK_REGEX, PHONE_MASK_REPLACEMENT);
    }
}
