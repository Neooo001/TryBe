package com.trybe.wardrobe.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户自定义衣服分类树
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("wardrobe_category")
public class WardrobeCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 父分类ID，0 表示一级分类 */
    private Long parentId;

    /** 层级：1-一级, 2-二级 */
    private Integer level;

    /** 一级分类业务码，二级分类为 null */
    private String catCode;

    private String name;

    /** 排序号，越小越靠前 */
    private Integer sortNo;

    /** 是否系统内置：0-用户自建, 1-系统模板（不可删除） */
    private Integer isSystem;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
