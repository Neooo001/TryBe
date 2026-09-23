package com.trybe.user.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户角色返回（升级为创作者后回传）
 *
 * @author TryBe Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("用户角色")
public class UserRoleVO {

    @ApiModelProperty("用户 ID")
    private Long userId;

    @ApiModelProperty("角色：USER / CREATOR / ADMIN")
    private String role;
}
