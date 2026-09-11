package com.trybe.wardrobe.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 淘宝分享链接导入请求
 */
@Data
@ApiModel(description = "淘宝分享链接导入请求")
public class TaobaoImportReq {

    @ApiModelProperty(value = "淘宝分享文案（含短链/淘口令，如【淘宝】https://e.tb.cn/xxx ...）", required = true)
    @NotBlank(message = "分享文案不能为空")
    private String shareText;
}
