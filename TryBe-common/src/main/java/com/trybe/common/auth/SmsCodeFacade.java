package com.trybe.common.auth;

import cn.hutool.core.util.StrUtil;
import com.trybe.common.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 短信验证码门面：图形码 → 三级限流 → 发码 → 标记已发。
 *
 * <p>把「图形码校验 + 频率限流 + 发送 + 标记」这段编排收敛为唯一实现，消除
 * 旧代码 {@code CreatorAuthServiceImpl.sendRegisterCode} 与新 {@code /users/auth/**}
 * 的重复编排，保证单一修复点。
 *
 * <p><b>职责边界</b>：只做编排与顺序保证。图形码细节在 {@link CaptchaStore}、
 * 限流在 {@link SmsRateLimiter}、发码/校验在 {@link SmsService}，本类不改动底层。
 *
 * <p><b>事务</b>：本类方法包含外部交互（短信/Redis），<b>严禁</b>加 {@code @Transactional}，
 * 避免长事务占用数据库连接。
 *
 * <p><b>模块位置</b>：位于 {@code TryBe-common}，只依赖 common 内组件，无 {@code com.trybe.user.*} 依赖。
 *
 * @author TryBe Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsCodeFacade {

    private static final String PHONE_MASK_REGEX = "(\\d{3})\\d{4}(\\d{4})";
    private static final String PHONE_MASK_REPLACEMENT = "$1****$2";

    private final CaptchaStore captchaStore;
    private final SmsRateLimiter smsRateLimiter;
    private final SmsService smsService;

    /**
     * 发送短信验证码：先限流再验图形码（避免攻击者用无效图形码反复触发短信），
     * 最后发码并 markSent（保证验证码在注册/登录成功前不被消费）。
     *
     * <p>{@link SmsService#sendVerifyCode(String)} 内部还有 60 秒发送间隔校验。
     *
     * @param phone       手机号
     * @param captchaId   图形验证码标识
     * @param captchaCode 图形验证码
     * @param clientIp    客户端 IP（用于 IP 维度限流）
     * @param scene       使用场景（仅语义标注）
     */
    public void sendCode(String phone, String captchaId, String captchaCode, String clientIp, SmsScene scene) {
        // 1. 三级日限流（手机号 / IP / 全局）——超限抛 SMS_TOO_FREQUENT(406)
        smsRateLimiter.check(phone, clientIp);
        // 2. 图形验证码校验——失败抛 CAPTCHA_ERROR(105)
        captchaStore.verify(captchaId, captchaCode, phone);
        // 3. 发码（内含 60 秒间隔，超频抛 406）
        smsService.sendVerifyCode(phone);
        // 4. 标记图形码已用于发码
        captchaStore.markSent(captchaId);
        log.info("[sms-code] 验证码已发送 phone={} ip={} scene={}",
                maskPhone(phone), clientIp, scene != null ? scene.name() : SmsScene.LOGIN.name());
    }

    /**
     * 仅校验图形验证码（不消费），供「密码登录」等前置校验复用。
     *
     * @param captchaId   图形验证码标识
     * @param captchaCode 图形验证码
     * @param phone       手机号（与图形码绑定一致性校验）
     */
    public void verifyCaptcha(String captchaId, String captchaCode, String phone) {
        captchaStore.verify(captchaId, captchaCode, phone);
    }

    /**
     * 消费图形验证码（注册 / 密码登录成功后调用，保证一次性）。
     *
     * @param captchaId 图形验证码标识
     */
    public void consumeCaptcha(String captchaId) {
        captchaStore.consume(captchaId);
    }

    /**
     * 仅校验短信验证码（wrap {@link SmsService#verifyCode(String, String)}），
     * 供短信登录、绑定/换绑手机号复用。
     *
     * @param phone 手机号
     * @param code  短信验证码
     * @return true=校验通过
     */
    public boolean verifySmsCode(String phone, String code) {
        return smsService.verifyCode(phone, code);
    }

    /**
     * 手机号脱敏：138****8000
     */
    private String maskPhone(String phone) {
        if (StrUtil.isBlank(phone)) {
            return "***";
        }
        return phone.replaceAll(PHONE_MASK_REGEX, PHONE_MASK_REPLACEMENT);
    }
}
