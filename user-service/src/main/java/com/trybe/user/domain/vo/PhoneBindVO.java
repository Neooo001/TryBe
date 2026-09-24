package com.trybe.user.domain.vo;

import com.trybe.user.enums.PhoneState;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 手机号绑定 / 查询返回 VO
 *
 * <p>用于 A5「绑定手机号」与 A6「查询当前手机号」两个端点，
 * Web 恢复态刷新时据此回填手机号与 {@code phoneState}。
 *
 * @author TryBe Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("手机号绑定返回")
public class PhoneBindVO {

    @ApiModelProperty("用户ID")
    private Long userId;

    @ApiModelProperty("当前绑定手机号（未绑定时为 null）")
    private String phone;

    @ApiModelProperty("手机号归属状态：0从未绑定 1已绑定 2曾绑定被释放")
    private PhoneState phoneState;
}
