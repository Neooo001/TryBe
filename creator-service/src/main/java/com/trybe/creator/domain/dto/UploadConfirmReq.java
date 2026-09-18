package com.trybe.creator.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 上传确认请求（P2）
 *
 * <p>服务端强校验 objectKey 必须以 {@code creator/{userId}/} 开头（越权防护）。
 */
@Data
@ApiModel(description = "上传确认请求")
public class UploadConfirmReq {

    @ApiModelProperty(value = "OSS objectKey（P1 返回的临时 key）", required = true)
    @NotBlank(message = "objectKey不能为空")
    private String objectKey;

    @ApiModelProperty(value = "上传场景 ITEM_ORIGIN/ITEM_CUTOUT/ITEM_REFERENCE/MODEL_IMAGE", required = true)
    @NotBlank(message = "上传场景不能为空")
    private String scene;

    @ApiModelProperty("物品业务编号（可选）")
    private String itemNo;
}
