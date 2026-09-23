package com.trybe.creator.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 物品详情视图对象（I2 / I3 / I4 出参）
 */
@Data
@ApiModel(description = "物品详情信息")
public class ItemDetailVO {

    @ApiModelProperty("物品ID")
    private Long id;

    @ApiModelProperty("物品业务编号")
    private String itemNo;

    @ApiModelProperty("创作者用户ID")
    private Long creatorId;

    @ApiModelProperty("一级分类")
    private String categoryCode;

    @ApiModelProperty("标题")
    private String title;

    @ApiModelProperty("描述")
    private String description;

    @ApiModelProperty("价格（元）")
    private BigDecimal price;

    @ApiModelProperty("状态：DRAFT/ON_SHELF/OFF_SHELF")
    private String status;

    @ApiModelProperty("试穿模式 FLAT/MODEL/BOTH")
    private String tryonMode;

    @ApiModelProperty("主色 HEX")
    private String colorHex;

    @ApiModelProperty("材质")
    private String material;

    @ApiModelProperty("图案/风格标签（JSON数组字符串）")
    private String patternTags;

    @ApiModelProperty("主图 OSS objectKey")
    private String imageKey;

    @ApiModelProperty("主图预签名访问 URL")
    private String imageUrl;

    @ApiModelProperty("透明底 PNG OSS objectKey")
    private String cutoutPngKey;

    @ApiModelProperty("透明底 PNG 预签名访问 URL")
    private String cutoutPngUrl;

    @ApiModelProperty("参考图 OSS objectKey 列表（JSON数组字符串）")
    private String referenceKeys;

    @ApiModelProperty("参考图预签名访问 URL 列表")
    private List<String> referenceUrls;

    @ApiModelProperty("实物宽度（毫米）")
    private Integer realWidthMm;

    @ApiModelProperty("实物高度（毫米）")
    private Integer realHeightMm;

    @ApiModelProperty("尺寸标定：每毫米像素数")
    private BigDecimal pxPerMm;

    @ApiModelProperty("锚点标注 JSON 字符串")
    private String anchorJson;

    @ApiModelProperty("演示模特ID（预留，v1 不启用）")
    private Long demoModelId;

    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty("更新时间")
    private LocalDateTime updateTime;

    @ApiModelProperty("模特图列表")
    private List<ModelImageVO> modelImages;
}
