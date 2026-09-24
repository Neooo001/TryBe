package com.trybe.common.auth;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.ShearCaptcha;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BizIllegalException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 图形验证码存储（Redis）
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>答案只在服务端</b>：验证码由 Hutool {@code ShearCaptcha} 生成（剪切+扭曲干扰，抗 OCR 强于 LineCaptcha），
 *       前端只拿到图片，只能提交猜测。这是它能起到防护作用的前提 —— 前端生成的验证码答案就在浏览器内存里，
 *       控制台可直接读取，等于零防护。</li>
 *   <li><b>一次性消费分段</b>：发短信阶段只校验不删除（置 {@code sent=1}），
 *       注册阶段校验通过后才真正 {@code DEL}，避免「发完短信验证码就失效导致注册卡住」。</li>
 *   <li><b>与手机号绑定</b>：防止拿 A 的图形码给 B 发短信。</li>
 *   <li><b>失败次数上限</b>：同一 captchaId 连续错 5 次即作废，防爆破。</li>
 * </ul>
 *
 * <p>Redis 结构：{@code captcha:{captchaId}} → Hash{code, phone, sent, failCount}，TTL 300s。
 *
 * <p><b>模块位置</b>：位于 {@code TryBe-common}，供 user-service 的新旧认证链路共用；
 * 仅依赖 hutool、Spring Data Redis 与 common 自身的 {@code ErrorCodeEnum}/{@code BizIllegalException}，
 * 不含任何 {@code com.trybe.user.*} 依赖（避免 common → user-service 反向依赖）。
 *
 * @author TryBe Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaptchaStore {

    private static final String KEY_PREFIX = "captcha:";
    private static final String FIELD_CODE = "code";
    private static final String FIELD_PHONE = "phone";
    private static final String FIELD_SENT = "sent";
    private static final String FIELD_FAIL = "failCount";

    /** 有效期（秒），与前端倒计时无关，仅限制验证码本身的可用时长 */
    public static final long TTL_SECONDS = 300L;
    /** 同一验证码最大校验失败次数 */
    private static final int MAX_FAIL_COUNT = 5;

    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final int CODE_COUNT = 4;
    private static final int THICKNESS = 4;

    private final StringRedisTemplate stringRedisTemplate;

    /** 一次性产出的验证码：标识 + 图片 */
    @Getter
    public static class CaptchaImage {
        private final String captchaId;
        private final String imageBase64;

        CaptchaImage(String captchaId, String imageBase64) {
            this.captchaId = captchaId;
            this.imageBase64 = imageBase64;
        }
    }

    /**
     * 生成验证码并落 Redis。
     *
     * @param phone 需要绑定的手机号，可为 null（不绑定）
     * @return 标识与图片（图片只在本次返回，不落 Redis，避免存储冗余）
     */
    public CaptchaImage create(String phone) {
        String captchaId = IdUtil.fastSimpleUUID();
        ShearCaptcha captcha = CaptchaUtil.createShearCaptcha(WIDTH, HEIGHT, CODE_COUNT, THICKNESS);

        String key = KEY_PREFIX + captchaId;
        stringRedisTemplate.opsForHash().put(key, FIELD_CODE, captcha.getCode());
        stringRedisTemplate.opsForHash().put(key, FIELD_PHONE, StrUtil.nullToEmpty(phone));
        stringRedisTemplate.opsForHash().put(key, FIELD_SENT, "0");
        stringRedisTemplate.opsForHash().put(key, FIELD_FAIL, "0");
        stringRedisTemplate.expire(key, TTL_SECONDS, TimeUnit.SECONDS);

        return new CaptchaImage(captchaId, captcha.getImageBase64());
    }

    /**
     * 校验图形验证码。
     *
     * <p>失败会累加 {@code failCount}，达到上限后该验证码作废。
     * 失败统一以 {@link ErrorCodeEnum#CAPTCHA_ERROR}（105）抛出，前端据此自动刷新图形码。
     *
     * @param captchaId   验证码标识
     * @param inputCode   用户输入的验证码
     * @param phone       当前操作的手机号，用于在绑定号码时做一致性校验
     */
    public void verify(String captchaId, String inputCode, String phone) {
        String key = KEY_PREFIX + captchaId;
        Object storedCode = stringRedisTemplate.opsForHash().get(key, FIELD_CODE);
        if (storedCode == null) {
            throw new BizIllegalException(ErrorCodeEnum.CAPTCHA_ERROR, "图形验证码已失效，请重新获取");
        }

        Object failObj = stringRedisTemplate.opsForHash().get(key, FIELD_FAIL);
        int failCount = failObj == null ? 0 : Integer.parseInt(String.valueOf(failObj));
        if (failCount >= MAX_FAIL_COUNT) {
            stringRedisTemplate.delete(key);
            throw new BizIllegalException(ErrorCodeEnum.CAPTCHA_ERROR, "图形验证码错误次数过多，请重新获取");
        }

        Object boundPhone = stringRedisTemplate.opsForHash().get(key, FIELD_PHONE);
        String bound = boundPhone == null ? "" : String.valueOf(boundPhone);
        if (StrUtil.isNotBlank(bound) && !bound.equals(StrUtil.nullToEmpty(phone))) {
            throw new BizIllegalException(ErrorCodeEnum.CAPTCHA_ERROR, "图形验证码与手机号不匹配，请重新获取");
        }

        if (!String.valueOf(storedCode).equalsIgnoreCase(StrUtil.trimToEmpty(inputCode))) {
            stringRedisTemplate.opsForHash().put(key, FIELD_FAIL, String.valueOf(failCount + 1));
            throw new BizIllegalException(ErrorCodeEnum.CAPTCHA_ERROR, "图形验证码不正确");
        }
    }

    /** 标记该验证码已用于发送短信（发码成功后再标记，避免未收到短信却占用验证码） */
    public void markSent(String captchaId) {
        stringRedisTemplate.opsForHash().put(KEY_PREFIX + captchaId, FIELD_SENT, "1");
    }

    /** 注册成功后消费：真正删除，保证一次性 */
    public void consume(String captchaId) {
        stringRedisTemplate.delete(KEY_PREFIX + captchaId);
    }
}
