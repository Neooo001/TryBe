package com.trybe.user.service;

import com.trybe.user.domain.dto.BindPhoneDTO;
import com.trybe.user.domain.dto.PasswordLoginDTO;
import com.trybe.user.domain.dto.SmsLoginDTO;
import com.trybe.user.domain.dto.SmsSendDTO;
import com.trybe.user.domain.vo.CaptchaVO;
import com.trybe.user.domain.vo.CreatorLoginVO;
import com.trybe.user.domain.vo.PhoneBindVO;

/**
 * 统一账号体系认证门面 —— {@code /users/auth/**} 的业务编排。
 *
 * <p>编排职责：图形码 / 三级限流 / 发码 / 登录（短信 / 密码）/ 绑定手机号。
 * 具体凭据解析与归属转移委托给 {@link UserIdentityService}，令牌下发委托给
 * {@code com.trybe.user.support.TokenIssuer}。
 *
 * @author TryBe Team
 */
public interface AuthFacadeService {

    /**
     * 生成图形验证码（A1）。
     *
     * @param phone 手机号，可为 null（不绑定）
     * @return 验证码标识与图片
     */
    CaptchaVO generateCaptcha(String phone);

    /**
     * 发送短信验证码（A2）：图形码 → 三级限流 → 发码 → markSent。
     *
     * @param dto      发码请求（含图形码）
     * @param clientIp 客户端 IP
     */
    void sendSmsCode(SmsSendDTO dto, String clientIp);

    /**
     * 短信验证码登录（A3）：校验短信码 → 命中当前持有者 / 未注册自动注册（D2）→ 发令牌。
     *
     * @param dto      登录请求（不含图形码）
     * @param clientIp 客户端 IP
     * @return 登录态（含 phoneState）
     */
    CreatorLoginVO loginBySms(SmsLoginDTO dto, String clientIp);

    /**
     * 密码登录（A4）：图形码校验 → 「当前持有者 → 历史持有者」优先级 + 密码校验 → 发令牌。
     *
     * <p>命中 ≠ 当前持有者时判定为「恢复匹配」，返回 {@code phoneState=RELEASED} 供前端引导补绑。
     *
     * @param dto      登录请求（含图形码）
     * @param clientIp 客户端 IP
     * @return 登录态（含 phoneState）
     */
    CreatorLoginVO loginByPassword(PasswordLoginDTO dto, String clientIp);

    /**
     * 绑定手机号（A5，需登录）：校验短信码 → 归属转移事务 → 返回新状态。
     *
     * @param userId 当前登录用户ID
     * @param dto    绑定请求（手机号 + 短信码）
     * @return 绑定结果
     */
    PhoneBindVO bindPhone(Long userId, BindPhoneDTO dto);

    /**
     * 查询当前手机号与归属状态（A6，需登录）。
     *
     * @param userId 当前登录用户ID
     * @return 手机号与状态
     */
    PhoneBindVO getPhone(Long userId);
}
