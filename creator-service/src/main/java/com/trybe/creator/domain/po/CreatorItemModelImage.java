package com.trybe.creator.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 创作者物品模特图表（模特上身图 + 自动质检结果 + 肖像授权记录）
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("creator_item_model_image")
public class CreatorItemModelImage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属物品 creator_item.id */
    private Long itemId;

    /** 创作者用户ID（冗余，便于按人查询） */
    private Long creatorId;

    /** 模特图 OSS objectKey */
    private String imageKey;

    /** 展示顺序 */
    private Integer sortOrder;

    /** 图片宽（px，质检时写入） */
    private Integer width;

    /** 图片高（px，质检时写入） */
    private Integer height;

    /** 质检状态：PENDING/PASS/WARN/FAIL */
    private String qcStatus;

    /** 质检明细 JSON：{"faceCount":1,"minSizeOk":true,...} */
    private String qcDetail;

    /** 创作者已勾选肖像授权声明：0否/1是 */
    private Integer authConfirmed;

    /** 授权声明文本版本号，如 v1.0 */
    private String authVersion;

    /** 授权确认时间 */
    private LocalDateTime authConfirmedAt;

    private LocalDateTime createTime;

    /** 逻辑删除标记：0-未删除, 1-已删除 */
    @TableLogic
    private Integer deleted;
}
