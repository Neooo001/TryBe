package com.trybe.creator.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * OSS 预签名上传请求（P1）
 *
 * <p>itemNo 可选：创作者通常先上传图片再创建物品，未绑定物品时归档到 pool 目录。
 */
@Data
@ApiModel(description = "OSS 预签名上传请求")
public class PresignReq {

    @ApiModelProperty(value = "上传场景 ITEM_ORIGIN/ITEM_CUTOUT/ITEM_REFERENCE/MODEL_IMAGE", required = true)
    @NotBlank(message = "上传场景不能为空")
    private String scene;

    @ApiModelProperty(value = "扩展名，如 png/jpg（CUTOUT 场景强制 png）", required = true)
    @NotBlank(message = "扩展名不能为空")
    private String ext;

    @ApiModelProperty("物品业务编号（可选，回填后归档到该物品目录）")
    private String itemNo;
}
