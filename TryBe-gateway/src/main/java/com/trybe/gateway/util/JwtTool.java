package com.trybe.gateway.util;

import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTValidator;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.UnauthorizedException;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.time.Duration;
import java.util.Date;

/**
 * JWT 工具类 — 双 Token 体系
 *
 * <p>新增 {@link #parseTokenWithExpire(String)} 方法，区分 "过期" 与 "签名错误/格式非法"，
 * 使 Gateway 过滤器可以返回 {@code 40101} vs {@code 40102}，前端据此决策刷新 or 重登。
 *
 * <p>旧 {@link #parseToken(String)} 标记为 {@code @Deprecated}，保留兼容。
 *
 * @author TryBe Team
 */
@Component
public class JwtTool {
    private final JWTSigner jwtSigner;

    public JwtTool(KeyPair keyPair) {
        this.jwtSigner = JWTSignerUtil.createSigner("rs256", keyPair);
    }

    // ==================== Token 生成 ====================

    /**
     * 创建 AccessToken（JWT）
     *
     * @param userId 用户ID
     * @param ttl    有效期
     * @return JWT AccessToken
     */
    public String createToken(Long userId, Duration ttl) {
        return JWT.create()
                .setPayload("user", userId)
                .setExpiresAt(new Date(System.currentTimeMillis() + ttl.toMillis()))
                .setSigner(jwtSigner)
                .sign();
    }

    // ==================== Token 校验（区分过期 vs 非法） ====================

    /**
     * 解析 Token 并区分过期 vs 非法
     *
     * <p>这是双 Token 体系推荐使用的解析方法。
     *
     * @param token JWT Token 字符串
     * @return 用户ID
     * @throws UnauthorizedException(ACCESS_TOKEN_EXPIRE)    — Token 签名正确但已过期 → 前端刷新
     * @throws UnauthorizedException(TOKEN_INVALID_RELOGIN)  — Token 签名错误/格式非法 → 前端重登
     */
    public Long parseTokenWithExpire(String token) {
        // 1. 校验是否为空
        if (token == null) {
            throw new UnauthorizedException(ErrorCodeEnum.TOKEN_INVALID_RELOGIN, "未登录");
        }

        // 2. 校验签名 & 格式
        JWT jwt;
        try {
            jwt = JWT.of(token).setSigner(jwtSigner);
        } catch (Exception e) {
            throw new UnauthorizedException(ErrorCodeEnum.TOKEN_INVALID_RELOGIN, "登录凭证无效");
        }

        if (!jwt.verify()) {
            throw new UnauthorizedException(ErrorCodeEnum.TOKEN_INVALID_RELOGIN, "登录凭证无效");
        }

        // 3. 校验是否过期（签名正确 + 过期 → 40101）
        try {
            JWTValidator.of(jwt).validateDate();
        } catch (ValidateException e) {
            throw new UnauthorizedException(ErrorCodeEnum.ACCESS_TOKEN_EXPIRE, "登录凭证已过期");
        }

        // 4. 数据格式校验
        Object userPayload = jwt.getPayload("user");
        if (userPayload == null) {
            throw new UnauthorizedException(ErrorCodeEnum.TOKEN_INVALID_RELOGIN, "登录凭证无效");
        }

        // 5. 数据解析
        try {
            return Long.valueOf(userPayload.toString());
        } catch (RuntimeException e) {
            throw new UnauthorizedException(ErrorCodeEnum.TOKEN_INVALID_RELOGIN, "登录凭证无效");
        }
    }

    // ==================== 旧方法（兼容） ====================

    /**
     * @deprecated 双 Token 体系下请使用 {@link #parseTokenWithExpire(String)} 以区分过期与非法。
     * 保留此方法以兼容旧调用方。
     */
    @Deprecated
    public Long parseToken(String token) {
        if (token == null) {
            throw new UnauthorizedException("未登录");
        }
        JWT jwt;
        try {
            jwt = JWT.of(token).setSigner(jwtSigner);
        } catch (Exception e) {
            throw new UnauthorizedException("无效的token", e);
        }
        if (!jwt.verify()) {
            throw new UnauthorizedException("无效的token");
        }
        try {
            JWTValidator.of(jwt).validateDate();
        } catch (ValidateException e) {
            throw new UnauthorizedException("token已经过期");
        }
        Object userPayload = jwt.getPayload("user");
        if (userPayload == null) {
            throw new UnauthorizedException("无效的token");
        }
        try {
            return Long.valueOf(userPayload.toString());
        } catch (RuntimeException e) {
            throw new UnauthorizedException("无效的token");
        }
    }
}