package com.trybe.wardrobe.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 衣柜条目VO
 *
 * <p>字段顺序与小程序衣橱详情页展示顺序一致：
 * 状态 → 分类 → 季节 → 颜色 → 品牌/店铺 → 价格 → 穿着次数 → 单次穿着成本 → 购买日期 → 备注
 */
@Data
@ApiModel(description = "衣柜条目")
public class WardrobeItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 身份与来源 ====================

    @ApiModelProperty("衣柜条目ID")
    private Long wardrobeId;

    @ApiModelProperty("来源：AI_IMPORT/MANUAL/CUSTOM_OUTFIT（TAOBAO_IMPORT 为历史值）")
    private String source;

    @ApiModelProperty("来源中文名")
    private String sourceText;

    @ApiModelProperty("商品ID（历史字段）")
    private Long productId;

    @ApiModelProperty("商品数字ID（历史字段）")
    private Long numIid;

    // ==================== 图片 ====================

    @ApiModelProperty("展示主图临时访问URL（AI导入为裁剪后图片）")
    private String imageUrl;

    @ApiModelProperty("原图临时访问URL")
    private String originImageUrl;

    @ApiModelProperty("AI裁剪图临时访问URL")
    private String cutoutImageUrl;

    @ApiModelProperty("详情图临时访问URL列表")
    private List<String> detailImageUrls;

    // ==================== 基础信息 ====================

    @ApiModelProperty("标题")
    private String title;

    @ApiModelProperty("备注")
    private String remark;

    // ==================== AI 识别基础字段 ====================

    @ApiModelProperty("一级分类码：TOP/BOTTOM/SET/SHOES/BAG/ACCESSORY/DECOR/OTHER")
    private String categoryL1;

    @ApiModelProperty("一级分类中文名")
    private String categoryL1Name;

    @ApiModelProperty("二级分类名称")
    private String categoryL2;

    @ApiModelProperty("分类全称，如 下装-长裤")
    private String categoryName;

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

    @ApiModelProperty("适用季节中文列表")
    private List<String> seasonNames;

    @ApiModelProperty("穿着状态：WEARING/WISHLIST/IDLE/DISCARD")
    private String wearStatus;

    @ApiModelProperty("穿着状态中文名")
    private String wearStatusText;

    // ==================== 购物信息 ====================

    @ApiModelProperty("品牌/店铺")
    private String brand;

    @ApiModelProperty("价格")
    private BigDecimal price;

    @ApiModelProperty("穿着次数")
    private Integer wearCount;

    @ApiModelProperty("单次穿着成本")
    private BigDecimal costPerWear;

    @ApiModelProperty("购买日期")
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

    // ==================== AI 元信息 / 历史商品信息（淘宝导入已下线） ====================

    @ApiModelProperty("AI 是否已完成识别")
    private Integer aiRecognized;

    @ApiModelProperty("识别使用的模型")
    private String aiModel;

    @ApiModelProperty("识别置信度")
    private BigDecimal aiConfidence;

    @ApiModelProperty("原价（历史字段）")
    private String reservePrice;

    @ApiModelProperty("折后价/券后价（历史字段）")
    private String zkFinalPrice;

    @ApiModelProperty("商品落地页URL（历史字段）")
    private String itemUrl;

    @ApiModelProperty("店铺名称（历史字段）")
    private String shopTitle;

    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;
}
