package com.trybe.user.controller;

import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.domain.R;
import com.trybe.common.utils.UserContext;
import com.trybe.user.domain.vo.UserLoginVO;
import com.trybe.user.service.AuthService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 认证控制器 — 双 Token 体系
 *
 * <h3>接口说明</h3>
 * <ul>
 *   <li>{@code POST /auth/refresh-token} — 刷新 AccessToken（无需鉴权）</li>
 *   <li>{@code POST /auth/logout} — 主动登出（需鉴权）</li>
 * </ul>
 *
 * <h3>限流策略</h3>
 * <p>刷新接口使用内存级简单限流（ConcurrentHashMap），每个 IP 每分钟最多 10 次。
 * 生产环境可替换为 Redis 滑动窗口或 Sentinel。
 *
 * @author TryBe Team
 */
@Api(tags = "认证相关接口")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /** 内存级限流：IP → (时间窗口起点, 请求次数) */
    private static final Map<String, long[]> RATE_LIMITER = new ConcurrentHashMap<>();

    /** 限流窗口大小（毫秒） */
    private static final long RATE_LIMIT_WINDOW_MS = TimeUnit.MINUTES.toMillis(1);

    /** 单窗口最大请求数 */
    private static final int RATE_LIMIT_MAX = 10;

    @ApiOperation("刷新 AccessToken")
    @PostMapping("refresh-token")
    public R<UserLoginVO> refreshToken(@RequestBody Map<String, String> body,
                                        HttpServletRequest request) {
        String refreshToken = body.get("refreshToken");

        // 内存级限流
        String clientIp = getClientIp(request);
        if (!allowRequest(clientIp)) {
            log.warn("[RefreshToken] 限流触发 clientIp={}", clientIp);
            return R.error(ErrorCodeEnum.BIZ_ERROR, "操作过于频繁，请稍后再试");
        }

        UserLoginVO vo = authService.refreshAccessToken(refreshToken);
        return R.ok(vo);
    }

    @ApiOperation("用户登出")
    @PostMapping("logout")
    public R<Void> logout(HttpServletRequest request) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            // 防御性处理
            return R.error(ErrorCodeEnum.TOKEN_INVALID_RELOGIN, "未登录");
        }
        String accessToken = extractAccessToken(request);
        authService.logout(userId, accessToken);
        return R.ok();
    }

    // ==================== 工具方法 ====================

    /**
     * 内存级滑动窗口限流
     */
    private boolean allowRequest(String clientIp) {
        long now = System.currentTimeMillis();
        long[] window = RATE_LIMITER.computeIfAbsent(clientIp, k -> new long[]{now, 0});
        synchronized (window) {
            if (now - window[0] > RATE_LIMIT_WINDOW_MS) {
                // 窗口过期，重置
                window[0] = now;
                window[1] = 1;
                return true;
            }
            if (window[1] >= RATE_LIMIT_MAX) {
                return false;
            }
            window[1]++;
            return true;
        }
    }

    /**
     * 从请求头提取 Bearer Token 原始字符串
     */
    private String extractAccessToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        // 兜底：尝试从 gateway 透传的 header 获取
        String userInfo = request.getHeader("user-info");
        log.debug("[Logout] 无法提取 AccessToken，user-info={}", userInfo);
        return null;
    }

    /**
     * 获取客户端真实 IP（考虑反向代理）
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
