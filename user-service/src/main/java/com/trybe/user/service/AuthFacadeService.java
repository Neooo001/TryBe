package com.trybe.user.service;

import com.trybe.user.domain.dto.BindPhoneDTO;
import com.trybe.user.domain.dto.PasswordLoginDTO;
import com.trybe.user.domain.dto.SmsSendAuthedDTO;
import com.trybe.user.domain.dto.SmsSendDTO;
import com.trybe.user.domain.vo.CaptchaVO;
import com.trybe.user.domain.vo.PhoneBindVO;
import com.trybe.user.domain.vo.UserLoginVO;

/**
 * wx 域（小程序）认证门面 —— {@code /users/auth/**} 的业务编排。
 *
 * <p><b>账号体系 v3</b>：本门面只服务小程序端，签发的令牌 realm=wx；
 * Web 创作者后台走 {@code /web/auth/**}，两端共用 {@code app_user}。
 *
 * <p>编排职责：图形码 / 限流 / 发码（免鉴权版 + 登录版）/ 密码登录 / 绑定手机号。
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
     * 发送短信验证码（A2，免鉴权）：图形码 → 限流 → 发码 → markSent。
     *
     * @param dto      发码请求（含图形码）
     * @param clientIp 客户端 IP
     */
    void sendSmsCode(SmsSendDTO dto, String clientIp);

    /**
     * 已登录用户发送短信验证码（A2b）：<b>无图形码</b>，靠登录态 + 限流防护。
     *
     * <p>场景差异决定 phone 从哪来：
     * <ul>
     *   <li>{@code BIND} —— 取请求体 phone（绑定/换绑新号码），并校验格式；</li>
     *   <li>{@code SET_PASSWORD} —— <b>忽略</b>请求体 phone，固定发到该账号当前已绑定号码，
     *       未绑定则抛 {@code NEED_BIND_PHONE}(407)。</li>
     * </ul>
     *
     * @param userId   当前登录用户ID
     * @param dto      发码请求（BIND 需带 phone）
     * @param clientIp 客户端 IP
     */
    void sendSmsCodeForLoginUser(Long userId, SmsSendAuthedDTO dto, String clientIp);

    /**
     * 密码登录（wx 域）：图形码校验 → 匹配当前手机号持有者 + 密码校验 → 发令牌（realm=wx）。
     *
     * @param dto      登录请求（含图形码）
     * @param clientIp 客户端 IP
     * @return 登录态（含 phoneState）
     */
    UserLoginVO loginByPassword(PasswordLoginDTO dto, String clientIp);

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
