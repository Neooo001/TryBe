package com.trybe.creator.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OSS 预签名直传视图对象（P1 出参）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "OSS 预签名直传信息")
public class PresignVO {

    @ApiModelProperty("OSS objectKey（临时 tmp 位，P2 confirm 时回传）")
    private String objectKey;

    @ApiModelProperty("预签名 PUT 上传 URL（前端直传 OSS 使用，日志禁止打印）")
    private String uploadUrl;

    @ApiModelProperty("URL 有效期（秒）")
    private Long expireSeconds;
}
