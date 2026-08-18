package com.trybe.material.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trybe.material.domain.enums.CheckStatus;
import com.trybe.material.domain.enums.CheckType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 素材校验日志表
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("material_check_log")
public class MaterialCheckLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 素材ID
     */
    private Long materialId;

    /**
     * 校验类型
     */
    private CheckType checkType;

    /**
     * 校验结果：0-待处理, 1-处理中, 2-通过, 3-失败
     */
    private CheckStatus status;

    /**
     * 校验结果详情(JSON)
     */
    private String resultDetail;

    /**
     * 校验耗时(毫秒)
     */
    private Long costMs;

    /**
     * 校验开始时间
     */
    private LocalDateTime startTime;

    /**
     * 校验结束时间
     */
    private LocalDateTime endTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
