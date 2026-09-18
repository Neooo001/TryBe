package com.trybe.creator.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 模特图绑定请求（M1）
 *
 * <p>肖像授权声明：authConfirmed 必须为 true（前端先展示文案让用户勾选），
 * 否则提交时时参数错误（100）。
 */
@Data
@ApiModel(description = "模特图绑定请求")
public class ModelImageBindReq {

    @ApiModelProperty(value = "模特图 OSS objectKey（P2 confirm 后回传）", required = true)
    @NotBlank(message = "模特图不能为空")
    @Size(max = 255, message = "模特图路径过长")
    private String imageKey;

    @ApiModelProperty("展示顺序，默认 0")
    private Integer sortOrder;

    @ApiModelProperty(value = "已确认肖像授权声明", required = true)
    @NotNull(message = "请先确认肖像授权声明")
    private Boolean authConfirmed;

    @ApiModelProperty("授权声明文本版本号，如 v1.0")
    @Size(max = 16, message = "授权声明版本号过长")
    private String authVersion;
}
