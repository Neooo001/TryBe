package com.trybe.api.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * <p>
 * 素材DTO — 跨服务传输对象
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "素材信息")
public class MaterialDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("素材ID")
    private Long materialId;

    @ApiModelProperty("用户ID")
    private Long userId;

    @ApiModelProperty("素材类型：FACE-人脸, HAND-手部")
    private String materialType;

    @ApiModelProperty("OSS存储路径")
    private String objectKey;

    @ApiModelProperty("校验状态：0-待上传, 1-校验中, 2-校验通过, 3-校验失败")
    private Integer status;

    @ApiModelProperty("校验失败原因")
    private String failReason;

    @ApiModelProperty("临时访问URL（有效期1小时）")
    private String accessUrl;
}
