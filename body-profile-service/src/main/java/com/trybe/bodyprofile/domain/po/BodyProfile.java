package com.trybe.bodyprofile.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trybe.bodyprofile.domain.enums.BodyType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 身形档案主表（每用户唯一，身型数据作为档案自身字段）
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("body_profile")
public class BodyProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID（唯一，单档案）
     */
    private Long userId;

    /**
     * 档案名称
     */
    private String name;

    /**
     * 身高(cm)
     */
    private BigDecimal height;

    /**
     * 体重(kg)
     */
    private BigDecimal weight;

    /**
     * 胸围(cm)
     */
    private BigDecimal bust;

    /**
     * 腰围(cm)
     */
    private BigDecimal waist;

    /**
     * 臀围(cm)
     */
    private BigDecimal hip;

    /**
     * 肩宽(cm)
     */
    private BigDecimal shoulder;

    /**
     * 臂长(cm)
     */
    private BigDecimal arm;

    /**
     * 腿长(cm)
     */
    private BigDecimal leg;

    /**
     * 体型标签
     */
    private BodyType bodyType;

    /**
     * 肤色：冷白皮/暖白皮/自然色/小麦色/古铜色
     */
    private String skinTone;

    /**
     * 期望修饰部位（JSON数组，如 ["大腿","腰腹"]）
     */
    private String modifyParts;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
