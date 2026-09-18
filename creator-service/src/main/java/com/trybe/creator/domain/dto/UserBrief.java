package com.trybe.creator.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * user 表只读投影（creator-service 禁止写 user 表，仅读取 id/nickname）
 */
@Data
@ApiModel(description = "用户简要信息（只读投影）")
public class UserBrief {

    @ApiModelProperty("用户ID")
    private Long id;

    @ApiModelProperty("用户昵称")
    private String nickname;
}
