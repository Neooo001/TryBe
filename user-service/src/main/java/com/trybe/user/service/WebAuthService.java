package com.trybe.user.service;

import com.trybe.user.domain.dto.WebLoginDTO;
import com.trybe.user.domain.dto.WebPasswordChangeDTO;
import com.trybe.user.domain.dto.WebRegisterDTO;
import com.trybe.user.domain.vo.CaptchaVO;
import com.trybe.user.domain.vo.WebLoginVO;

/**
 * Web 域（创作者后台）认证服务 —— {@code /web/auth/**}
 *
 * <p><b>与 wx 域零关联</b>：本服务只读写 {@code web_user} / {@code web_user_archive}，
 * 不触碰 {@code wx_user} 与 {@code user_identity}；签发的令牌 realm=web。
 *
 * <p>事务边界沿用项目硬约定：门面方法含外部交互（图形码 Redis / 令牌 Redis），
 * 一律不加 {@code @Transactional}；纯 DB 操作下沉到 {@code WebUserTxService}。
 *
 * @author TryBe Team
 */
public interface WebAuthService {

    /**
     * 获取图形验证码（免鉴权）。与手机号绑定，防止拿 A 的码给 B 用。
     */
    CaptchaVO generateCaptcha(String phone);

    /**
     * 注册（免鉴权，注册即登录）：图形码校验 → 建号（含沉睡归档判定）→ 签发令牌（realm=web）。
     */
    WebLoginVO register(WebRegisterDTO dto, String clientIp);

    /**
     * 登录（免鉴权）：图形码校验 → 手机号+密码校验（统一文案防枚举）→ 签发令牌（realm=web）。
     */
    WebLoginVO login(WebLoginDTO dto, String clientIp);

    /**
     * 修改密码（需登录，realm=web）：旧密码校验 → 更新哈希 → 本域全部 RefreshToken 失效。
     */
    void changePassword(Long userId, WebPasswordChangeDTO dto);
}
