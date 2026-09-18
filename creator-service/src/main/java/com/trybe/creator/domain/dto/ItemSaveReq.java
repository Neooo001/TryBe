package com.trybe.creator.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * 物品新建/编辑请求（I3 / I4）
 *
 * <p>编辑语义（红线⑧）：<b>字段为 null = 不修改</b>，服务端用 LambdaUpdateWrapper
 * 只 set 非 null 字段，全字段覆盖会把未传的字段抹成空。
 */
@Data
@ApiModel(description = "物品保存请求")
public class ItemSaveReq {

    @ApiModelProperty("物品标题（新建必填）")
    @Size(max = 64, message = "标题不能超过64个字符")
    private String title;

    @ApiModelProperty("一级分类 TOP/BOTTOM/SET/SHOES/BAG/ACCESSORY/DECOR/OTHER（新建必填）")
    private String categoryCode;

    @ApiModelProperty("描述")
    @Size(max = 1024, message = "描述不能超过1024个字符")
    private String description;

    @ApiModelProperty("价格（元）")
    private BigDecimal price;

    @ApiModelProperty("试穿模式 FLAT/MODEL/BOTH，默认 FLAT")
    private String tryonMode;

    @ApiModelProperty("主色 HEX，如 #RRGGBB")
    @Size(max = 9, message = "主色格式不正确")
    private String colorHex;

    @ApiModelProperty("材质")
    @Size(max = 64, message = "材质不能超过64个字符")
    private String material;

    @ApiModelProperty("图案/风格标签数组")
    private List<String> patternTags;

    @ApiModelProperty("主图 OSS objectKey（P2 上传确认后回传）")
    @Size(max = 255, message = "主图路径过长")
    private String imageKey;

    @ApiModelProperty("透明底 PNG OSS objectKey")
    @Size(max = 255, message = "透明底图路径过长")
    private String cutoutPngKey;

    @ApiModelProperty("参考图 objectKey 数组")
    private List<String> referenceKeys;

    @ApiModelProperty("实物宽度（毫米）")
    private Integer realWidthMm;

    @ApiModelProperty("实物高度（毫米）")
    private Integer realHeightMm;

    @ApiModelProperty("尺寸标定：每毫米像素数（前端 Canvas 标定后回传）")
    private BigDecimal pxPerMm;

    @ApiModelProperty("锚点标注 JSON 对象（前端 Canvas 标注后回传，如 {\"neck\":[x,y]}）")
    private Object anchorJson;
}
