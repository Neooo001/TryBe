package com.trybe.api.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * <p>
 * 身形档案 DTO — 跨服务传输对象
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "身形档案信息")
public class BodyProfileDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("档案ID")
    private Long profileId;

    @ApiModelProperty("用户ID")
    private Long userId;

    @ApiModelProperty("档案名称")
    private String name;

    @ApiModelProperty("身高(cm)")
    private BigDecimal height;

    @ApiModelProperty("体重(kg)")
    private BigDecimal weight;

    @ApiModelProperty("胸围(cm)")
    private BigDecimal bust;

    @ApiModelProperty("腰围(cm)")
    private BigDecimal waist;

    @ApiModelProperty("臀围(cm)")
    private BigDecimal hip;

    @ApiModelProperty("肩宽(cm)")
    private BigDecimal shoulder;

    @ApiModelProperty("臂长(cm)")
    private BigDecimal arm;

    @ApiModelProperty("腿长(cm)")
    private BigDecimal leg;

    @ApiModelProperty("体型标签")
    private String bodyType;

    @ApiModelProperty("肤色")
    private String skinTone;

    @ApiModelProperty("期望修饰部位")
    private List<String> modifyParts;

    @ApiModelProperty("备注")
    private String remark;

    @ApiModelProperty("关联素材列表")
    private List<BodyProfileMaterialDTO> materials;
}
