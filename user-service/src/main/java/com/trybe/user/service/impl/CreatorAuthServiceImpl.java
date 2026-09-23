package com.trybe.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BizIllegalException;
import com.trybe.common.exception.UnauthorizedException;
import com.trybe.common.service.OssService;
import com.trybe.common.service.SmsService;
import com.trybe.user.config.JwtProperties;
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
import com.trybe.user.util.CaptchaStore;
import com.trybe.user.util.JwtTool;
import com.trybe.user.util.RefreshTokenUtil;
import com.trybe.user.util.SmsRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 创作者端认证服务实现
 *
 * <p>与小程序侧登录（{@code PhoneServiceImpl} / {@code UserServiceImpl}）是两条独立链路：
 * 本实现不调用它们的任何方法，只用 common 基建（JwtTool / RefreshTokenUtil / PasswordEncoder /
 * OssService / SmsService）与本地数据访问层 {@link UserMapper}。
 *
 * <p>注册语义：**注册即成为创作者** —— 写入 {@code role=CREATOR} 并直接下发双 Token，
 * 不再经历入驻申请与运营审核。
 *
 * <p>登录语义：**放行 USER** —— 小程序与后台共用 user 表，普通用户登录后台后由前端
 * 引导其升级为创作者，而不是直接 403。
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreatorAuthServiceImpl implements ICreatorAuthService {

    private static final String PHONE_MASK_REGEX = "(\\d{3})\\d{4}(\\d{4})";
    private static final String PHONE_MASK_REPLACEMENT = "$1****$2";

    private static final String DEFAULT_NICKNAME = "新创作者";
    private static final String ROLE_USER = "USER";
    private static final String ROLE_CREATOR = "CREATOR";
    private static final String ROLE_ADMIN = "ADMIN";

    /** creator-service 的 RoleGuard 角色缓存前缀，升级后必须清除，否则 30 分钟内仍按 USER 判定 */
    private static final String ROLE_CACHE_KEY_PREFIX = "creator:role:";

    /** 对外统一文案：不区分"手机号不存在"与"密码错误"，避免账号枚举 */
    private static final String CREDENTIAL_ERROR_MSG = "手机号或密码错误";

    private final UserMapper userMapper;
    private final JwtTool jwtTool;
    private final JwtProperties jwtProperties;
    private final RefreshTokenUtil refreshTokenUtil;
    private final PasswordEncoder passwordEncoder;
    private final OssService ossService;
    private final SmsService smsService;
    private final CaptchaStore captchaStore;
    private final SmsRateLimiter smsRateLimiter;
    private final StringRedisTemplate stringRedisTemplate;

    // ==================== 注册 ====================

    /**
     * 创作者注册
     *
     * <p>校验顺序：图形验证码 → 短信验证码 → 手机号唯一性 → 写入。
     * 图形验证码在注册成功后才真正消费（一次性）。
     */
    @Override
    @Transactional
    public CreatorLoginVO register(CreatorRegisterDTO dto) {
        String phone = dto.getPhone().trim();

        captchaStore.verify(dto.getCaptchaId(), dto.getCaptchaCode(), phone);

        if (!smsService.verifyCode(phone, dto.getCode())) {
            throw new BizIllegalException(ErrorCodeEnum.BIZ_ERROR, "短信验证码错误或已失效");
        }

        User exist = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (exist != null) {
            log.warn("[creatorAuth] 注册失败：手机号已存在 phone={} role={}", maskPhone(phone), exist.getRole());
            if (ROLE_USER.equals(StrUtil.trimToEmpty(exist.getRole()))) {
                throw new BizIllegalException(
                        ErrorCodeEnum.BIZ_ERROR, "该手机号已注册，请直接登录并在后台申请成为创作者");
            }
            throw new BizIllegalException(ErrorCodeEnum.BIZ_ERROR, "该手机号已注册，请直接登录");
        }

        User user = new User()
                .setPhone(phone)
                .setPassword(passwordEncoder.encode(dto.getPassword()))
                .setNickname(StrUtil.isBlank(dto.getNickname()) ? DEFAULT_NICKNAME : dto.getNickname().trim())
                .setStatus(UserStatus.NORMAL)
                .setRole(ROLE_CREATOR);
        userMapper.insert(user);

        captchaStore.consume(dto.getCaptchaId());

        log.info("[creatorAuth] 创作者注册成功 userId={} phone={}", user.getId(), maskPhone(phone));
        return buildLoginVO(user);
    }

    // ==================== 登录 ====================

    /**
     * 创作者登录
     *
     * <p>刻意不校验 role：共用账号体系下 USER 登录后台是正常路径，
     * 由前端根据 VO 中的 role 决定落地页（USER → /upgrade）。
     */
    @Override
    public CreatorLoginVO login(CreatorLoginDTO dto) {
        String phone = dto.getPhone().trim();
        String maskedPhone = maskPhone(phone);

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user == null) {
            log.warn("[creatorAuth] 登录失败：手机号未注册 phone={}", maskedPhone);
            throw new UnauthorizedException(ErrorCodeEnum.AUTH_FAILED, CREDENTIAL_ERROR_MSG);
        }

        if (StrUtil.isBlank(user.getPassword())) {
            log.warn("[creatorAuth] 登录失败：账号未设置密码 userId={}", user.getId());
            throw new UnauthorizedException(ErrorCodeEnum.AUTH_FAILED, CREDENTIAL_ERROR_MSG);
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            log.warn("[creatorAuth] 登录失败：密码错误 userId={}", user.getId());
            throw new UnauthorizedException(ErrorCodeEnum.AUTH_FAILED, CREDENTIAL_ERROR_MSG);
        }

        if (user.getStatus() == UserStatus.FROZEN) {
            log.warn("[creatorAuth] 登录失败：账号已冻结 userId={}", user.getId());
            throw new UnauthorizedException(ErrorCodeEnum.ACCOUNT_FROZEN, "账号异常，请联系客服");
        }

        log.info("[creatorAuth] 登录成功 userId={} role={}", user.getId(), user.getRole());
        return buildLoginVO(user);
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

        // 先过限流，再验图形码：避免攻击者用无效图形码反复触发短信
        smsRateLimiter.check(phone, clientIp);
        captchaStore.verify(dto.getCaptchaId(), dto.getCaptchaCode(), phone);

        // SmsService 内部还有 60 秒发送间隔校验
        smsService.sendVerifyCode(phone);

        captchaStore.markSent(dto.getCaptchaId());
        log.info("[creatorAuth] 注册验证码已发送 phone={} ip={}", maskPhone(phone), clientIp);
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
     * 组装登录返回（含双 Token 下发）
     */
    private CreatorLoginVO buildLoginVO(User user) {
        Duration accessTtl = jwtProperties.getAccessTokenTTL();
        String accessToken = jwtTool.createToken(user.getId(), accessTtl);
        String refreshToken = refreshTokenUtil.generateToken();
        // 网页注册的账号没有 openid；Redis Hash 不接受 null 值，统一转空串避免写入异常
        refreshTokenUtil.saveToken(refreshToken, user.getId(), StrUtil.nullToEmpty(user.getOpenid()));

        CreatorLoginVO vo = new CreatorLoginVO();
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole());
        vo.setAccessToken(accessToken);
        vo.setRefreshToken(refreshToken);
        vo.setExpiresIn(accessTtl.getSeconds());

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
