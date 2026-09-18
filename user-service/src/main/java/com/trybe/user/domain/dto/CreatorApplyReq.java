package com.trybe.user.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 创作者入驻申请请求（U3）
 */
@Data
@ApiModel(description = "创作者入驻申请请求")
public class CreatorApplyReq {

    @ApiModelProperty(value = "店铺/品牌名", required = true)
    @NotBlank(message = "店铺名不能为空")
    @Size(max = 64, message = "店铺名不能超过64个字符")
    private String shopName;

    @ApiModelProperty("店铺简介")
    @Size(max = 512, message = "店铺简介不能超过512个字符")
    private String shopIntro;

    @ApiModelProperty(value = "联系电话", required = true)
    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "联系电话格式不正确")
    private String contactPhone;

    @ApiModelProperty("资质图 OSS objectKey（U2 预签名上传后回传，可空）")
    @Size(max = 255, message = "资质图路径过长")
    private String licenseImageKey;

    @ApiModelProperty("经营类目，逗号分隔一级分类：TOP,BOTTOM,...（可空）")
    @Size(max = 128, message = "经营类目过长")
    private String categoryScope;
}
