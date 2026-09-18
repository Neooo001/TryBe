package com.trybe.creator.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审核流水视图对象（R4 出参）
 */
@Data
@ApiModel(description = "审核流水信息")
public class AuditRecordVO {

    @ApiModelProperty("流水ID")
    private Long id;

    @ApiModelProperty("动作：SUBMIT/APPROVE/REJECT/ON_SHELF/OFF_SHELF")
    private String action;

    @ApiModelProperty("操作人 user.id")
    private Long operatorId;

    @ApiModelProperty("原因/备注")
    private String reason;

    @ApiModelProperty("操作时间")
    private LocalDateTime createTime;
}
