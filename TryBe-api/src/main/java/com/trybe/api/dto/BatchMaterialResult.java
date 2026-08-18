package com.trybe.api.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 批量素材校验/查询结果 — 跨服务传输对象
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "批量素材处理结果")
public class BatchMaterialResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("有效的素材列表")
    private List<MaterialDTO> validMaterials;

    @ApiModelProperty("无效的素材ID列表")
    private List<Long> invalidMaterialIds;
}
