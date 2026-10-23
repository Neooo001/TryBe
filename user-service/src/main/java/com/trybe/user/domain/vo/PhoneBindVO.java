package com.trybe.user.domain.vo;

import com.trybe.user.enums.PhoneState;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 手机号绑定 / 查询返回 VO
 *
 * <p>用于 A5「绑定手机号」与 A6「查询当前手机号」两个端点。
 *
 * <p><b>v3 新增「合并」语义</b>：绑定一个已被其他账号持有的手机号时，当前账号会被
 * 并入持有者（数据随手机号走），此时 {@link #merged}=true 且 {@link #userId} 变为目标账号，
 * <b>同时下发一套新令牌</b> —— 前端必须整体替换本地登录态，否则后续请求仍在用已作废账号的 token。
 *
 * @author TryBe Team
 */
@Data
@NoArgsConstructor
@ApiModel("手机号绑定返回")
public class PhoneBindVO {

    @ApiModelProperty("用户ID（合并时为目标账号ID）")
    private Long userId;

    @ApiModelProperty("当前绑定手机号（未绑定时为 null）")
    private String phone;

    @ApiModelProperty("手机号归属状态：0从未绑定 1已绑定 2曾绑定被释放")
    private PhoneState phoneState;

    @ApiModelProperty("是否发生了账号合并（true 表示登录身份已切换）")
    private boolean merged;

    @ApiModelProperty("昵称（合并后为目标账号昵称，供前端刷新本地缓存）")
    private String nickname;

    @ApiModelProperty("是否已设置密码（false 时前端应引导设置密码）")
    private boolean hasPassword;

    // ===== 仅在 merged=true 时下发，前端需整套替换 =====

    @ApiModelProperty("JWT AccessToken（仅合并时返回）")
    private String accessToken;

    @ApiModelProperty("RefreshToken（仅合并时返回）")
    private String refreshToken;

    @ApiModelProperty("AccessToken 剩余有效时间（秒，仅合并时返回）")
    private long expiresIn;

    /**
     * 兼容旧调用方的三参构造（不涉及合并的查询/幂等绑定场景）。
     */
    public PhoneBindVO(Long userId, String phone, PhoneState phoneState) {
        this.userId = userId;
        this.phone = phone;
        this.phoneState = phoneState;
    }
}
