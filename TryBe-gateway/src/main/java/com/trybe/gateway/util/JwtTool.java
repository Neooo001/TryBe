package com.trybe.gateway.util;

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

/**
 * JWT 工具类 — <b>只解析不签发</b>
 *
 * <p>区分 "过期" 与 "签名错误/格式非法"，使 Gateway 过滤器可以返回
 * {@code 40101} vs {@code 40102}，前端据此决策刷新 or 重登。
 *
 * <p><b>为何删除 createToken</b>：网关与 user-service 曾各持一份签发代码，
 * 签名/claim 极易漂移。签发职责收归 user-service 的 {@code TokenIssuer}，
 * 网关只做校验，杜绝双份实现。
 *
 * @author TryBe Team
 */
@Component
public class JwtTool {
    private final JWTSigner jwtSigner;

    public JwtTool(KeyPair keyPair) {
        this.jwtSigner = JWTSignerUtil.createSigner("rs256", keyPair);
    }

    // ==================== Token 校验（区分过期 vs 非法） ====================

    /**
     * Token 解析结果：用户ID + 账号域
     *
     * <p>双账号域拆分后，仅凭 userId 无法定位到人（wx 域 1 号 ≠ web 域 1 号），
     * 必须与 realm 一起使用。
     */
    @lombok.Data
    public static class ParsedToken {
        private final Long userId;
        private final String realm;

        public ParsedToken(Long userId, String realm) {
            this.userId = userId;
            this.realm = realm;
        }
    }

    /**
     * 解析 Token 并区分过期 vs 非法
     *
     * <p>这是双 Token 体系推荐使用的解析方法。
     *
     * @param token JWT Token 字符串
     * @return 解析结果（用户ID + 账号域）
     * @throws UnauthorizedException(ACCESS_TOKEN_EXPIRE)    — Token 签名正确但已过期 → 前端刷新
     * @throws UnauthorizedException(TOKEN_INVALID_RELOGIN)  — Token 签名错误/格式非法 → 前端重登
     */
    public ParsedToken parseTokenWithExpire(String token) {
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

        // 5. 数据解析（userId + realm）
        try {
            Long userId = Long.valueOf(userPayload.toString());
            Object realmPayload = jwt.getPayload(RealmConstants.CLAIM_REALM);
            String realm = realmPayload == null ? null : realmPayload.toString();
            return new ParsedToken(userId, realm);
        } catch (RuntimeException e) {
            throw new UnauthorizedException(ErrorCodeEnum.TOKEN_INVALID_RELOGIN, "登录凭证无效");
        }
    }
}