package com.trybe.wardrobe.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 分类节点保存请求
 *
 * <p>两个语义复用同一个 DTO：
 * <ul>
 *   <li>新建：只传 parentId + name（id 为空）</li>
 *   <li>批量保存（拖拽排序 / 重命名）：传 id + name + sortNo</li>
 * </ul>
 */
@Data
@ApiModel(description = "分类节点保存请求")
public class CategoryNodeReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("分类ID，新建时为空")
    private Long id;

    @ApiModelProperty("父分类ID，0 表示一级分类")
    private Long parentId;

    @ApiModelProperty("分类名称")
    private String name;

    @ApiModelProperty("排序号，越小越靠前")
    private Integer sortNo;
}
