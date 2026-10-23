package com.trybe.creator.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 创作者物品表（创作者商品池，与 wardrobe_item / product 无外键关联）
 * </p>
 *
 * <p><b>字段分两段，靠 {@code ai} 前缀区分归属，绝不允许两个来源写同一列：</b>
 * <ol>
 *   <li><b>创作者可写</b>（发布页 / 作品编辑页填的那些，也是唯一会返回给前端的内容）：
 *       title / description / linkUrl / zone / patternTags / seasons / styles / topics /
 *       material / imageKey / price / categoryCode / tryonMode / status</li>
 *   <li><b>AI 独占</b>（{@code ai} 前缀开头，只由 {@code GarmentInfoFillServiceImpl} 后台回填，
 *       {@code ItemSaveReq} / {@code ItemDetailVO} 里一律不出现）：
 *       aiProductName / aiCategoryCode / aiColorHex / aiColorName / aiSeasons / aiStyles /
 *       aiMaterial / aiSellingPoints / aiSuggestedPrice / aiParsed / aiModel / aiParsedAt</li>
 * </ol>
 *
 * <p>为什么必须分列：AI 解析的「季节 / 款式 / 材质」与创作者手填的是<b>两份数据</b>，
 * 合进同一列会让「创作者视图」被平台数据污染，也会出现「谁覆盖谁」的扯不清。
 * 见 sql/creatorService/creator_item_字段改造.sql。
 *
 * <p>试穿标定字段（cutoutPngKey / referenceKeys / realWidthMm / realHeightMm /
 * pxPerMm / anchorJson / demoModelId）本轮不开放编辑，属预留。
 *
 * <p>状态与角色相关字段统一用 String 存储，规避枚举类型处理器配置风险。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("creator_item")
public class CreatorItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 物品业务编号，如 CI20250101000001（全局唯一） */
    private String itemNo;

    /** 创作者用户ID（app_user.id） */
    private Long creatorId;

    /** 【创作者】一级分类：TOP/BOTTOM/SET/SHOES/BAG/ACCESSORY/DECOR/OTHER */
    private String categoryCode;

    /** 状态：DRAFT草稿/ON_SHELF已上架/OFF_SHELF已下架 */
    private String status;

    /** 试穿模式：FLAT/MODEL/BOTH */
    private String tryonMode;

    /** 【创作者】作品分区：上衣/下衣/连衣裙/外套/鞋靴/包袋配饰/发色/美瞳 */
    private String zone;

    // ==================== 创作者可写的作品内容 ====================

    /** 【创作者】作品标题 */
    private String title;

    /** 【创作者】作品描述 */
    private String description;

    /** 【创作者】价格（元） */
    private BigDecimal price;

    /** 【创作者】作品外链（购买链接 / 同款链接） */
    private String linkUrl;

    /** 【创作者】内容标签，JSON数组字符串 */
    private String patternTags;

    /** 【创作者】适用季节，JSON数组字符串：["SPRING","SUMMER","AUTUMN","WINTER"] */
    private String seasons;

    /** 【创作者】衣服款式，JSON数组字符串，如 ["直筒裤","休闲","高腰"] */
    private String styles;

    /** 【创作者】作品话题，JSON数组字符串（内容栏目） */
    private String topics;

    /** 【创作者】材质成分，如 棉95%/氨纶5% */
    private String material;

    // ==================== 素材 ====================

    /** 【创作者】当前主图 OSS objectKey（必须是本作品图片之一） */
    private String imageKey;

    /** 透明底 PNG OSS objectKey（试穿素材，预留） */
    private String cutoutPngKey;

    /** 参考图 objectKey 列表，JSON数组（预留） */
    private String referenceKeys;

    // ==================== 试穿尺寸标定（预留，v1 不启用） ====================

    /** 实物宽度（毫米） */
    private Integer realWidthMm;

    /** 实物高度（毫米） */
    private Integer realHeightMm;

    /** 尺寸标定结果：每毫米像素数（前端 Canvas 标定后回传） */
    private BigDecimal pxPerMm;

    /** 锚点标注 JSON：{"neck":[x,y],"shoulderL":[x,y],...} */
    private String anchorJson;

    // ==================== AI 独占（ai 前缀；创作者接口不读不写） ====================

    /** 【AI】商品名，推广用，与作品标题 title 解耦 */
    private String aiProductName;

    /** 【AI】判定的一级分类码（与创作者的 categoryCode 各存一份，不合并） */
    private String aiCategoryCode;

    /** 【AI】主色 HEX，如 #RRGGBB */
    private String aiColorHex;

    /** 【AI】主色中文名，如 浅灰蓝 */
    private String aiColorName;

    /** 【AI】判定季节，JSON数组（与 seasons 各存一份，不合并） */
    private String aiSeasons;

    /** 【AI】风格/款式标签，JSON数组（与 styles 各存一份，不合并） */
    private String aiStyles;

    /** 【AI】材质判定（与 material 各存一份，不合并） */
    private String aiMaterial;

    /** 【AI】推广卖点文案（限 40 字） */
    private String aiSellingPoints;

    /** 【AI】建议零售价（元），仅供参考，与实际售价 price 无关 */
    private BigDecimal aiSuggestedPrice;

    /** 【AI】是否已解析：0否/1是 */
    private Integer aiParsed;

    /** 【AI】执行解析的模型名，如 qwen-vl-max */
    private String aiModel;

    /** 【AI】解析时间 */
    private LocalDateTime aiParsedAt;

    // ==================== 预留与审计 ====================

    /** 演示模特ID（预留字段，v1 不启用，属 tryon-service） */
    private Long demoModelId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 逻辑删除标记：0-未删除, 1-已删除 */
    @TableLogic
    private Integer deleted;
}
