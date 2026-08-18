package com.trybe.material.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 预签名上传URL请求
 */
@Data
@ApiModel(description = "预签名上传URL请求")
public class PreSignUploadReq {

    @ApiModelProperty(value = "素材类型：FACE-人脸, HAND-手部", required = true)
    @NotBlank(message = "素材类型不能为空")
    private String materialType;

    @ApiModelProperty(value = "原始文件名", required = true)
    @NotBlank(message = "文件名不能为空")
    private String fileName;

    @ApiModelProperty(value = "文件大小(字节)")
    @NotNull(message = "文件大小不能为空")
    private Long fileSize;

    @ApiModelProperty(value = "文件MIME类型")
    private String contentType;
}
