package com.trybe.user.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 已登录用户发送短信验证码请求（{@code POST /users/auth/sms/code/authed}）
 *
 * <p><b>为什么单独建一个 DTO</b>：
 * 免鉴权的 {@link SmsSendDTO} 强制要求 {@code captchaId} / {@code captchaCode}（图形验证码是它唯一的
 * 自动化防线）。本接口面向已登录用户——登录态本身就是强凭据，无需图形码，因此不能复用那个 DTO。
 *
 * <p><b>防护模型（两层，无全局 / IP / 用户级维度）</b>：
 * <ol>
 *   <li>登录态：网关 {@code AuthGlobalFilter} fail-closed，无 token 直接 40102；</li>
 *   <li>频率：手机号 100 条/天（与免鉴权版同一实现 {@code SmsRateLimiter.check}）
 *       + {@code SmsService} 内置 60 秒发送间隔。</li>
 * </ol>
 *
 * <p><b>phone 的语义随 scene 变化</b>：
 * <ul>
 *   <li>{@code BIND}：phone 必填，由 Service 校验 {@code ^1[3-9]\d{9}$}；</li>
 *   <li>{@code SET_PASSWORD}：<b>忽略</b>请求体里的 phone，服务端固定发到该账号当前已绑定的号码，
 *       杜绝「发到 A 号却用库里的 B 号校验」的错位。</li>
 * </ul>
 *
 * <p>phone 刻意<b>不加</b> {@code @Pattern}：它对 SET_PASSWORD 是可选字段，
 * 加 Pattern 会让空串报错（{@code @Pattern} 对空串也会触发校验失败）。格式校验下沉到 Service。
 *
 * @author TryBe Team
 */
@Data
@ApiModel("已登录用户发送短信验证码请求（免图形码）")
public class SmsSendAuthedDTO {

    @ApiModelProperty(value = "手机号（BIND 必填；SET_PASSWORD 忽略，服务端取已绑定号码）",
            example = "13800138000")
    private String phone;

    @NotBlank(message = "使用场景不能为空")
    @ApiModelProperty(value = "使用场景：BIND-绑定/换绑手机号, SET_PASSWORD-设置密码",
            required = true, example = "BIND")
    private String scene;
}
