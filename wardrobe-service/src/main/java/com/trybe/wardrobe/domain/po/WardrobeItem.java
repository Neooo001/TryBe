package com.trybe.wardrobe.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * <p>
 * 衣柜条目表
 * </p>
 *
 * <p>字段分三段：
 * <ol>
 *   <li>图片与来源 — imageKey / originImageKey / cutoutImageKey / source</li>
 *   <li>AI 识别基础字段 — category / color / seasons / wearStatus</li>
 *   <li>推荐扩展字段 — style / material / occasions / size / warmth / formality ...</li>
 * </ol>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("wardrobe_item")
public class WardrobeItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 来源：AI_IMPORT / MANUAL / CUSTOM_OUTFIT（TAOBAO_IMPORT 为已下线功能的历史值） */
    private String source;

    /** 关联 product.id（历史字段，仅老的淘宝导入数据有值） */
    private Long productId;

    /** 标题 */
    private String title;

    /** 展示主图 OSS objectKey（AI 导入时指向裁剪结果图） */
    private String imageKey;

    /** 原图 OSS objectKey（用户上传原图） */
    private String originImageKey;

    /** AI 裁剪图 OSS objectKey */
    private String cutoutImageKey;

    /** 详情图 OSS objectKey 列表(JSON数组) */
    private String detailImageKeys;

    /** 备注 */
    private String remark;

    // ==================== AI 识别基础字段 ====================

    /** 一级分类：TOP/BOTTOM/SET/SHOES/BAG/ACCESSORY/DECOR/OTHER */
    private String categoryL1;

    /** 二级分类名称 */
    private String categoryL2;

    /** 关联用户自定义分类节点 wardrobe_category.id */
    private Long categoryId;

    /** 主色（中文） */
    private String color;

    /** 主色 HEX */
    private String colorHex;

    /** 颜色列表(JSON数组) */
    private String colors;

    /** 适用季节，逗号分隔 SPRING,SUMMER,AUTUMN,WINTER */
    private String seasons;

    /** 穿着状态：WEARING/WISHLIST/IDLE/DISCARD */
    private String wearStatus;

    // ==================== 用户可编辑 / 购物信息 ====================

    private String brand;

    private BigDecimal price;

    private Integer wearCount;

    /** 单次穿着成本，由服务端在 price / wearCount 变化时计算 */
    private BigDecimal costPerWear;

    private LocalDate purchaseDate;

    // ==================== 智能推荐扩展字段 ====================

    private String style;

    private String styles;

    private String material;

    private String occasions;

    private String size;

    private String fit;

    private String pattern;

    private String thickness;

    /** 保暖度 1-5 */
    private Integer warmth;

    /** 正式度 1-5 */
    private Integer formality;

    private String tags;

    // ==================== AI 处理元信息 ====================

    /** AI识别是否完成：0-否, 1-是 */
    private Integer aiRecognized;

    private String aiModel;

    private BigDecimal aiConfidence;

    private String aiRawJson;

    // ==================== 审计字段 ====================

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 逻辑删除标记：0-未删除, 1-已删除 */
    @TableLogic
    private Integer deleted;
}
