package com.trybe.user.service;

import com.trybe.user.domain.vo.UserLoginVO;

/**
 * 认证服务 — 双 Token 体系
 *
 * <p>负责 AccessToken 刷新、登出等与鉴权直接相关的业务逻辑。
 * 微信登录逻辑保留在 {@code IUserService.loginByWechat()}，不在此接口中重复。
 *
 * @author TryBe Team
 */
public interface AuthService {

    /**
     * 使用 RefreshToken 刷新 AccessToken
     *
     * <p>校验 RefreshToken 有效性后：
     * <ol>
     *   <li>生成新的 AccessToken（JWT，30min 有效）</li>
     *   <li>执行滚动续期：重置 RefreshToken 的 Redis TTL 为 7 天</li>
     * </ol>
     *
     * @param refreshToken 客户端提交的 RefreshToken（随机字符串）
     * @return 新的 AccessToken + expiresIn（不含新的 RefreshToken，复用当前）
     */
    UserLoginVO refreshAccessToken(String refreshToken);

    /**
     * 用户登出
     *
     * <p>操作：
     * <ol>
     *   <li>删除该用户<b>本域内</b>所有的 RefreshToken（Redis）</li>
     *   <li>将当前 AccessToken 加入黑名单，TTL = 剩余有效期</li>
     * </ol>
     *
     * @param userId      当前用户ID
     * @param accessToken 当前请求的 AccessToken（JWT）
     */
    void logout(Long userId, String accessToken);

    /**
     * 指定域的登出（供 Web 域改密后强制下线等场景显式指定域使用）
     *
     * @param userId      当前用户ID
     * @param accessToken 当前请求的 AccessToken（JWT）
     * @param realm       账号域：{@code wx} / {@code web}
     */
    void logout(Long userId, String accessToken, String realm);
}
