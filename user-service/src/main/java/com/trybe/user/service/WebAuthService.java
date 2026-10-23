package com.trybe.user.service;

import com.trybe.user.domain.dto.SmsSendDTO;
import com.trybe.user.domain.dto.WebLoginDTO;
import com.trybe.user.domain.dto.WebPasswordChangeDTO;
import com.trybe.user.domain.dto.WebRegisterDTO;
import com.trybe.user.domain.dto.WebSetPasswordDTO;
import com.trybe.user.domain.dto.WebSmsLoginDTO;
import com.trybe.user.domain.vo.CaptchaVO;
import com.trybe.user.domain.vo.WebLoginVO;

/**
 * Web 端（创作者后台）认证服务 —— {@code /web/auth/**}
 *
 * <p><b>账号体系 v3</b>：Web 端与小程序端共用 {@code app_user}，不再有独立的 {@code web_user}。
 * 手机号是唯一身份锚点：
 * <ul>
 *   <li>注册 = 按手机号「取或建」账号：已存在（小程序先注册）则直接沿用其数据</li>
 *   <li>短信登录 = 只认已存在的手机号，不新建</li>
 *   <li>密码登录 = 手机号 + 密码 + 图形码</li>
 * </ul>
 *
 * <p>事务边界沿用项目硬约定：门面方法含外部交互（图形码 Redis / 令牌 Redis），
 * 一律不加 {@code @Transactional}；纯 DB 操作下沉到 {@code UserIdentityService}。
 *
 * @author TryBe Team
 */
public interface WebAuthService {

    /**
     * 获取图形验证码（免鉴权）。与手机号绑定，防止拿 A 的码给 B 用。
     */
    CaptchaVO generateCaptcha(String phone);

    /**
     * 发送短信验证码（免鉴权）：图形码 → 限流 → 发码。用于注册 / 短信登录。
     */
    void sendSmsCode(SmsSendDTO dto, String clientIp);

    /**
     * 发送「设置密码」短信验证码（需登录，免图形码）：固定发到当前账号已绑定号码。
     */
    void sendSetPasswordCode(Long userId, String clientIp);

    /**
     * 注册（免鉴权，注册即登录）。
     *
     * <p>手机号已存在 → 沿用该账号数据（不新建、不覆盖昵称）；不存在 → 新建并随机生成昵称。
     */
    WebLoginVO register(WebRegisterDTO dto, String clientIp);

    /**
     * 短信验证码登录（免鉴权），手机号未注册时提示先注册。
     */
    WebLoginVO loginBySms(WebSmsLoginDTO dto, String clientIp);

    /**
     * 密码登录（免鉴权）：手机号 + 密码 + 图形码。
     */
    WebLoginVO login(WebLoginDTO dto, String clientIp);

    /**
     * 首次设置密码（需登录，短信码验证本人手机号）。
     */
    void setPassword(Long userId, WebSetPasswordDTO dto);

    /**
     * 修改密码（需登录，旧密码校验）→ 本域全部 RefreshToken 失效。
     */
    void changePassword(Long userId, WebPasswordChangeDTO dto);
}
