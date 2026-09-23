package com.trybe.user.service;

import com.trybe.user.domain.dto.CreatorLoginDTO;
import com.trybe.user.domain.dto.CreatorRegisterDTO;
import com.trybe.user.domain.dto.CreatorSendCodeDTO;
import com.trybe.user.domain.vo.CaptchaVO;
import com.trybe.user.domain.vo.CreatorLoginVO;
import com.trybe.user.domain.vo.UserRoleVO;

/**
 * 创作者端认证服务
 *
 * <p>职责边界：只负责创作者后台的注册与登录，与小程序侧登录接口
 * （{@code AuthService} / {@code IPhoneService}）彻底分离，互不复用：
 * <ul>
 *   <li>注册即授予 {@code CREATOR} 角色，注册成功直接返回双 Token，无需入驻申请与人工审核；</li>
 *   <li>登录<b>放行 USER 角色</b> —— 小程序与后台共用同一张 user 表，普通用户用同一手机号登录后台
 *       是合法路径，登录后由前端引导至「成为创作者」，而不是被 403 拒之门外；</li>
 *   <li>数据访问层直接使用 {@code UserMapper}（同一张 user 表），不依赖小程序侧 Service。</li>
 * </ul>
 *
 * @author TryBe Team
 */
public interface ICreatorAuthService {

    /**
     * 创作者注册：校验图形验证码与短信验证码后写入 {@code role=CREATOR} 的用户，并直接下发双 Token
     *
     * @param dto 注册请求
     * @return 登录态信息
     */
    CreatorLoginVO register(CreatorRegisterDTO dto);

    /**
     * 创作者登录：校验手机号与密码、账号状态；<b>不再校验角色</b>，USER 亦可进入后台
     *
     * @param dto 登录请求
     * @return 登录态信息（VO 中回填真实 role，供前端决定落地页）
     */
    CreatorLoginVO login(CreatorLoginDTO dto);

    /**
     * 生成图形验证码（免鉴权）
     *
     * @param phone 需要绑定的手机号，可为 null
     * @return 验证码标识与图片
     */
    CaptchaVO generateCaptcha(String phone);

    /**
     * 发送注册短信验证码（免鉴权）
     *
     * <p>已叠加三重防护：图形验证码 + 手机号/IP/全局三级限流 + SmsService 自带的 60 秒间隔。
     *
     * @param dto     发码请求
     * @param clientIp 客户端 IP，用于 IP 维度限流
     */
    void sendRegisterCode(CreatorSendCodeDTO dto, String clientIp);

    /**
     * 普通用户升级为创作者（需登录）
     *
     * <p>会同步清除 creator-service 的角色缓存 {@code creator:role:{userId}}，
     * 否则该用户 30 分钟内仍被判定为 USER。
     *
     * @param userId 当前登录用户 ID
     * @return 升级后的角色信息
     */
    UserRoleVO upgradeToCreator(Long userId);
}
