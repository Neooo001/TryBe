package com.trybe.wardrobe.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 分类节点VO（树形）
 */
@Data
@ApiModel(description = "分类节点")
public class CategoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("分类ID")
    private Long id;

    @ApiModelProperty("父分类ID，0 表示一级分类")
    private Long parentId;

    @ApiModelProperty("层级：1-一级, 2-二级")
    private Integer level;

    @ApiModelProperty("一级分类业务码")
    private String catCode;

    @ApiModelProperty("分类名称")
    private String name;

    @ApiModelProperty("排序号")
    private Integer sortNo;

    @ApiModelProperty("是否系统内置（不可删除）")
    private Integer isSystem;

    @ApiModelProperty("子分类")
    private List<CategoryVO> children = new ArrayList<>();
}
