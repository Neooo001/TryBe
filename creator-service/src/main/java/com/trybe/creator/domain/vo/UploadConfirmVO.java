package com.trybe.creator.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 上传确认视图对象（P2 出参）
 *
 * <p>hasAlpha 仅在 CUTOUT 场景有值：true 表示检测到透明通道（后端兜底校验结果）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "上传确认结果")
public class UploadConfirmVO {

    @ApiModelProperty("归档后的最终 OSS objectKey")
    private String objectKey;

    @ApiModelProperty("归档后文件的预签名访问 URL")
    private String accessUrl;

    @ApiModelProperty("图片宽（px）")
    private Integer width;

    @ApiModelProperty("图片高（px）")
    private Integer height;

    @ApiModelProperty("是否含透明通道（仅 CUTOUT 场景返回）")
    private Boolean hasAlpha;
}
