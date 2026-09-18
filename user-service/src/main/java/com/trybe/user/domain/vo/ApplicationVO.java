package com.trybe.user.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创作者入驻申请视图对象（U3/U4/U5 出参）
 */
@Data
@ApiModel(description = "创作者入驻申请信息")
public class ApplicationVO {

    @ApiModelProperty("申请ID")
    private Long id;

    @ApiModelProperty("申请用户ID")
    private Long userId;

    @ApiModelProperty("申请人昵称（U5 审核列表展示用）")
    private String nickname;

    @ApiModelProperty("店铺/品牌名")
    private String shopName;

    @ApiModelProperty("店铺简介")
    private String shopIntro;

    @ApiModelProperty("联系电话")
    private String contactPhone;

    @ApiModelProperty("资质图预签名访问 URL（服务端由 objectKey 转换，日志禁止打印）")
    private String licenseUrl;

    @ApiModelProperty("经营类目，逗号分隔")
    private String categoryScope;

    @ApiModelProperty("状态：PENDING待审/APPROVED通过/REJECTED驳回")
    private String status;

    @ApiModelProperty("驳回原因")
    private String rejectReason;

    @ApiModelProperty("审核人ID")
    private Long reviewerId;

    @ApiModelProperty("审核时间")
    private LocalDateTime reviewedAt;

    @ApiModelProperty("申请时间")
    private LocalDateTime createTime;
}
