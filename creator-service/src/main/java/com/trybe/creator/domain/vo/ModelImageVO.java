package com.trybe.creator.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模特图视图对象（M1 / M2 出参，含质检明细与授权记录）
 */
@Data
@ApiModel(description = "模特图信息")
public class ModelImageVO {

    @ApiModelProperty("模特图ID")
    private Long id;

    @ApiModelProperty("模特图预签名访问 URL")
    private String imageUrl;

    @ApiModelProperty("展示顺序")
    private Integer sortOrder;

    @ApiModelProperty("质检状态：PENDING/PASS/WARN/FAIL")
    private String qcStatus;

    @ApiModelProperty("质检明细 JSON 字符串")
    private String qcDetail;

    @ApiModelProperty("是否已确认肖像授权：0否/1是")
    private Integer authConfirmed;

    @ApiModelProperty("授权声明文本版本号")
    private String authVersion;

    @ApiModelProperty("授权确认时间")
    private LocalDateTime authConfirmedAt;

    @ApiModelProperty("图片宽（px）")
    private Integer width;

    @ApiModelProperty("图片高（px）")
    private Integer height;

    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;
}
