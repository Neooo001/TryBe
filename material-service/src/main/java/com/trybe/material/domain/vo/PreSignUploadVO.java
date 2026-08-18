package com.trybe.material.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

/**
 * 预签名上传URL响应
 */
@Data
@Builder
@ApiModel(description = "预签名上传URL响应")
public class PreSignUploadVO {

    @ApiModelProperty("素材ID")
    private Long materialId;

    @ApiModelProperty("预签名上传URL")
    private String uploadUrl;

    @ApiModelProperty("OSS存储路径")
    private String objectKey;

    @ApiModelProperty("URL过期时间(秒)")
    private Long expireSeconds;
}
