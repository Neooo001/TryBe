package com.trybe.material.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 素材信息VO
 */
@Data
@ApiModel(description = "素材信息")
public class MaterialVO {

    @ApiModelProperty("素材ID")
    private Long materialId;

    @ApiModelProperty("素材类型：FACE-人脸, HAND-手部")
    private String materialType;

    @ApiModelProperty("OSS存储路径")
    private String objectKey;

    @ApiModelProperty("原始文件名")
    private String fileName;

    @ApiModelProperty("文件大小(字节)")
    private Long fileSize;

    @ApiModelProperty("文件MIME类型")
    private String contentType;

    @ApiModelProperty("校验状态：0-待上传, 1-校验中, 2-校验通过, 3-校验失败")
    private Integer status;

    @ApiModelProperty("校验失败原因")
    private String failReason;

    @ApiModelProperty("临时访问URL")
    private String accessUrl;

    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;
}
