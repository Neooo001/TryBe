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
 * <p>字段分四段：
 * <ol>
 *   <li>业务身份 — itemNo / creatorId / categoryCode / title / status</li>
 *   <li>描述与售卖 — description / price / tryonMode / colorHex / material / patternTags</li>
 *   <li>素材 — imageKey / cutoutPngKey / referenceKeys</li>
 *   <li>试穿标定 — realWidthMm / realHeightMm / pxPerMm / anchorJson / demoModelId</li>
 * </ol>
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

    /** 物品业务编号，如 CI20250101000001 */
    private String itemNo;

    /** 创作者用户ID（user.id，role=CREATOR） */
    private Long creatorId;

    /** 一级分类：TOP/BOTTOM/SET/SHOES/BAG/ACCESSORY/DECOR/OTHER */
    private String categoryCode;

    /** 物品标题 */
    private String title;

    /** 描述 */
    private String description;

    /** 价格（元） */
    private BigDecimal price;

    /** 状态：DRAFT/PENDING/APPROVED/REJECTED/ON_SHELF/OFF_SHELF */
    private String status;

    /** 试穿模式：FLAT/MODEL/BOTH */
    private String tryonMode;

    /** 主色 HEX，如 #RRGGBB */
    private String colorHex;

    /** 材质 */
    private String material;

    /** 图案/风格标签，JSON数组字符串 */
    private String patternTags;

    /** 主图 OSS objectKey（白底/平铺展示图） */
    private String imageKey;

    /** 透明底 PNG OSS objectKey（试穿素材） */
    private String cutoutPngKey;

    /** 参考图 objectKey 列表，JSON数组 */
    private String referenceKeys;

    /** 实物宽度（毫米） */
    private Integer realWidthMm;

    /** 实物高度（毫米） */
    private Integer realHeightMm;

    /** 尺寸标定结果：每毫米像素数（前端 Canvas 标定后回传） */
    private BigDecimal pxPerMm;

    /** 锚点标注 JSON：{"neck":[x,y],"shoulderL":[x,y],...} */
    private String anchorJson;

    /** 演示模特ID（预留字段，v1 不启用，属 tryon-service） */
    private Long demoModelId;

    /** 最近一次驳回原因 */
    private String rejectReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 逻辑删除标记：0-未删除, 1-已删除 */
    @TableLogic
    private Integer deleted;
}
