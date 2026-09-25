package com.trybe.user.util;

import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTValidator;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.trybe.common.auth.RealmConstants;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.UnauthorizedException;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtTool {
    private final JWTSigner jwtSigner;

    public JwtTool(KeyPair keyPair) {
        this.jwtSigner = JWTSignerUtil.createSigner("rs256", keyPair);
    }

    /**
     * 创建 access-token（携带账号域标识）
     *
     * <p>双账号域拆分后，userId 只在域内唯一（wx 域 1 号与 web 域 1 号是两个人），
     * 故 token 必须同时携带 realm，由网关按路径校验，防止跨域访问。
     *
     * @param userId 用户ID
     * @param realm  账号域：{@link RealmConstants#WX} / {@link RealmConstants#WEB}
     * @param ttl    access-token 有效期
     * @return 签名后的 access-token
     */
    public String createToken(Long userId, String realm, Duration ttl) {
        // 1.生成jws
        return JWT.create()
                .setPayload("user", userId)
                .setPayload(RealmConstants.CLAIM_REALM, realm)
                .setExpiresAt(new Date(System.currentTimeMillis() + ttl.toMillis()))
                .setSigner(jwtSigner)
                .sign();
    }

    /**
     * 解析token（区分过期 vs 非法）
     *
     * @param token token
     * @return 解析刷新token得到的用户信息
     */
    public Long parseToken(String token) {
        // 1.校验token是否为空
        if (token == null) {
            throw new UnauthorizedException(ErrorCodeEnum.TOKEN_INVALID_RELOGIN, "未登录");
        }
        // 2.校验并解析jwt
        JWT jwt;
        try {
            jwt = JWT.of(token).setSigner(jwtSigner);
        } catch (Exception e) {
            throw new UnauthorizedException(ErrorCodeEnum.TOKEN_INVALID_RELOGIN, "登录凭证无效", e);
        }
        // 2.校验jwt是否有效
        if (!jwt.verify()) {
            // 验证失败
            throw new UnauthorizedException(ErrorCodeEnum.TOKEN_INVALID_RELOGIN, "登录凭证无效");
        }
        // 3.校验是否过期
        try {
            JWTValidator.of(jwt).validateDate();
        } catch (ValidateException e) {
            throw new UnauthorizedException(ErrorCodeEnum.ACCESS_TOKEN_EXPIRE, "登录凭证已过期");
        }
        // 4.数据格式校验
        Object userPayload = jwt.getPayload("user");
        if (userPayload == null) {
            // 数据为空
            throw new UnauthorizedException(ErrorCodeEnum.TOKEN_INVALID_RELOGIN, "登录凭证无效");
        }

        // 5.数据解析
        try {
           return Long.valueOf(userPayload.toString());
        } catch (RuntimeException e) {
            // 数据格式有误
            throw new UnauthorizedException(ErrorCodeEnum.TOKEN_INVALID_RELOGIN, "登录凭证无效");
        }
    }
}
