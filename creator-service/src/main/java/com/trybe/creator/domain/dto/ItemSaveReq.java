package com.trybe.creator.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Size;
import java.util.List;

/**
 * 物品新建/编辑请求（I3 / I4）
 *
 * <p><b>只放「创作者能管的字段」</b> —— 也就是发布作品时填的那些：
 * 标题 / 描述 / 链接 / 分区 / 标签 / 话题 / 季节 / 衣服款式 / 材质成分 / 主图。
 * 详情接口 {@code ItemDetailVO} 的字段集与本类一一对齐：能被写进来的，才是能被看到的。
 *
 * <p><b>刻意不在这里的字段</b>（不是漏了，是不允许创作者写）：
 * <ul>
 *   <li><b>所有 {@code ai} 前缀字段</b>：AI 解析出的商品名、分类、主色、季节、款式、材质、
 *       卖点、建议售价。这是平台侧推广数据，与创作者手填的 seasons / styles / material
 *       是<b>两份独立数据</b>（靠 {@code ai_} 前缀分列，不合并）。
 *       若把它们留在请求体里，创作者只要自己调一次接口就能把平台的解析结果改掉。</li>
 *   <li><b>试穿素材与尺寸标定</b>：透明底图、参考图、实物宽高、像素标定、锚点标注。本轮不开放编辑。</li>
 * </ul>
 *
 * <p><b>增删图片不走这里</b>：图片是独立资源，走
 * {@code POST /creator/items/{id}/model-images}（新增）、
 * {@code DELETE /creator/model-images/{imageId}}（删除）。
 * 本类的 {@code imageKey} 只用于「换主图」，且必须命中该作品已绑定的图片（校验在服务层）。
 *
 * <p>编辑语义（红线⑧）：<b>字段为 null = 不修改</b>，服务端用 LambdaUpdateWrapper
 * 只 set 非 null 字段，全字段覆盖会把未传的字段抹成空。
 */
@Data
@ApiModel(description = "物品保存请求（仅创作者可管理的字段）")
public class ItemSaveReq {

    @ApiModelProperty("作品标题（新建必填；编辑时 null = 不修改）")
    @Size(max = 256, message = "标题不能超过256个字符")
    private String title;

    @ApiModelProperty("作品描述（传空串表示清空）")
    @Size(max = 2048, message = "描述不能超过2048个字符")
    private String description;

    @ApiModelProperty("作品外链，如购买链接/同款链接（传空串表示清空）")
    @Size(max = 512, message = "链接不能超过512个字符")
    private String linkUrl;

    @ApiModelProperty("作品分区：上衣/下衣/连衣裙/外套/鞋靴/包袋配饰/发色/美瞳")
    @Size(max = 16, message = "分区名称过长")
    private String zone;

    @ApiModelProperty("作品标签数组（创作者自己填的，最多 8 个）")
    private List<String> patternTags;

    @ApiModelProperty("作品话题数组（内容栏目，最多 5 个）")
    private List<String> topics;

    @ApiModelProperty("适用季节码数组：SPRING/SUMMER/AUTUMN/WINTER（创作者手填，与 AI 的 aiSeasons 各存一份）")
    private List<String> seasons;

    @ApiModelProperty("衣服款式数组，如 [\"直筒裤\",\"休闲\",\"高腰\"]（创作者手填，与 AI 的 aiStyles 各存一份）")
    @Size(max = 12, message = "款式最多 12 个")
    private List<String> styles;

    @ApiModelProperty("材质成分，如 棉95%/氨纶5%（创作者手填，与 AI 的 aiMaterial 各存一份）")
    @Size(max = 128, message = "材质成分不能超过128个字符")
    private String material;

    @ApiModelProperty("主图 OSS objectKey：编辑时必须是该作品已有图片之一（换主图）")
    @Size(max = 512, message = "主图路径过长")
    private String imageKey;

    // ==================== 以下两项仅新建时生效，编辑时服务端忽略 ====================

    @ApiModelProperty("一级分类 TOP/BOTTOM/SET/SHOES/BAG/ACCESSORY/DECOR/OTHER（新建必填，编辑忽略）")
    @Size(max = 32, message = "分类码过长")
    private String categoryCode;

    @ApiModelProperty("试穿模式 FLAT/MODEL/BOTH，默认 FLAT（新建生效，编辑忽略）")
    private String tryonMode;
}
