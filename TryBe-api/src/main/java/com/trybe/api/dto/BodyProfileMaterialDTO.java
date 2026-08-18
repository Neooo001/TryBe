package com.trybe.api.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * <p>
 * 档案关联素材 DTO — 跨服务传输对象
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "档案关联素材信息")
public class BodyProfileMaterialDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("素材ID")
    private Long materialId;

    @ApiModelProperty("素材类型：FACE-人脸, HAND-手部")
    private String materialType;

    @ApiModelProperty("是否主图：0-否, 1-是")
    private Integer isPrimary;

    @ApiModelProperty("排序")
    private Integer sortOrder;

    @ApiModelProperty("临时访问URL（有效期1小时）")
    private String accessUrl;
}
