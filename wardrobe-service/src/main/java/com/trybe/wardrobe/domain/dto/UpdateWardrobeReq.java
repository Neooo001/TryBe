package com.trybe.wardrobe.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 衣柜条目编辑请求（全字段）
 *
 * <p>采用「非 null 即更新」语义：前端只提交用户改动过的字段，
 * 未提交（null）的字段保持原值，避免误清空 AI 识别结果。
 */
@Data
@ApiModel(description = "衣柜条目编辑请求")
public class UpdateWardrobeReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("标题")
    private String title;

    @ApiModelProperty("备注")
    private String remark;

    // ==================== 基础识别字段 ====================

    @ApiModelProperty("一级分类码：TOP/BOTTOM/SET/SHOES/BAG/ACCESSORY/DECOR/OTHER")
    private String categoryL1;

    @ApiModelProperty("二级分类名称，如 长裤")
    private String categoryL2;

    @ApiModelProperty("关联用户自定义分类节点ID")
    private Long categoryId;

    @ApiModelProperty("主色")
    private String color;

    @ApiModelProperty("主色HEX")
    private String colorHex;

    @ApiModelProperty("颜色列表")
    private List<String> colors;

    @ApiModelProperty("适用季节码列表：SPRING/SUMMER/AUTUMN/WINTER")
    private List<String> seasons;

    @ApiModelProperty("穿着状态：WEARING/WISHLIST/IDLE/DISCARD")
    private String wearStatus;

    // ==================== 购物信息 ====================

    @ApiModelProperty("品牌/店铺")
    private String brand;

    @ApiModelProperty("价格")
    private BigDecimal price;

    @ApiModelProperty("穿着次数")
    private Integer wearCount;

    @ApiModelProperty("购买日期，格式 yyyy-MM-dd")
    private LocalDate purchaseDate;

    // ==================== 推荐扩展字段 ====================

    @ApiModelProperty("主风格")
    private String style;

    @ApiModelProperty("风格标签列表")
    private List<String> styles;

    @ApiModelProperty("材质")
    private String material;

    @ApiModelProperty("适用场合列表")
    private List<String> occasions;

    @ApiModelProperty("尺码")
    private String size;

    @ApiModelProperty("版型")
    private String fit;

    @ApiModelProperty("图案")
    private String pattern;

    @ApiModelProperty("厚薄：THIN/MEDIUM/THICK")
    private String thickness;

    @ApiModelProperty("保暖度 1-5")
    private Integer warmth;

    @ApiModelProperty("正式度 1-5")
    private Integer formality;

    @ApiModelProperty("标签列表")
    private List<String> tags;
}
