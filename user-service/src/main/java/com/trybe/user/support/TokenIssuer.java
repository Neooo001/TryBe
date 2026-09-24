package com.trybe.user.support;

import cn.hutool.core.util.StrUtil;
import com.trybe.user.config.JwtProperties;
import com.trybe.user.util.JwtTool;
import com.trybe.user.util.RefreshTokenUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 统一双令牌签发器（user-service 本地）。
 *
 * <p>把「生成 AccessToken + 生成 RefreshToken + 落 Redis」收敛为唯一实现，
 * 杜绝各 Service 重复实现 {@code saveToken}，并集中修复已知缺陷 P5：
 * {@link RefreshTokenUtil#saveToken(String, Long, String)} 的 Redis Hash
 * <b>不接受 null openid</b>（{@code RefreshTokenUtil.java:111}），此处统一
 * {@link StrUtil#nullToEmpty(CharSequence)}。
 *
 * <p><b>为何放在 {@code com.trybe.user.support} 而非 {@code TryBe-common}</b>：
 * 本类依赖 user-service 本地的 {@link JwtTool} / {@link RefreshTokenUtil} / {@link JwtProperties}
 * （三者均在 {@code com.trybe.user.*}）。common 无法引用 user-service（user-service 依赖 common），
 * 放 common 会形成 <b>common → user-service 反向依赖 → 循环依赖 + 编译失败</b>。
 *
 * @author TryBe Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenIssuer {

    private final JwtTool jwtTool;
    private final JwtProperties jwtProperties;
    private final RefreshTokenUtil refreshTokenUtil;

    /**
     * 签发双令牌。
     *
     * @param userId 用户ID（非空）
     * @param openid 微信 openid，可为 null（Web/手机号登录场景），内部统一转空串
     * @return 令牌对（AccessToken + RefreshToken + 有效期秒数）
     */
    public TokenPair issue(Long userId, String openid) {
        Duration accessTtl = jwtProperties.getAccessTokenTTL();
        String accessToken = jwtTool.createToken(userId, accessTtl);
        String refreshToken = refreshTokenUtil.generateToken();

        // 关键：Redis Hash 不接受 null，统一 nullToEmpty（修复 P5）
        refreshTokenUtil.saveToken(refreshToken, userId, StrUtil.nullToEmpty(openid));

        log.info("[TokenIssuer] 令牌签发成功 userId={} accessTokenExpiresIn={}s refreshTokenMask={}",
                userId, accessTtl.getSeconds(), RefreshTokenUtil.maskToken(refreshToken));

        return new TokenPair(accessToken, refreshToken, accessTtl.getSeconds());
    }

    /**
     * 令牌对：AccessToken + RefreshToken + 有效期。
     */
    @Getter
    public static class TokenPair {
        /** JWT AccessToken */
        private final String accessToken;
        /** 随机字符串 RefreshToken（仅用于换取新的 AccessToken） */
        private final String refreshToken;
        /** AccessToken 剩余有效时间（秒） */
        private final long expiresIn;

        public TokenPair(String accessToken, String refreshToken, long expiresIn) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
        }
    }
}
