package com.trybe.wardrobe.domain.po;

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
 * 衣柜条目表
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("wardrobe_item")
public class WardrobeItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 来源：TAOBAO_IMPORT-淘宝导入, MANUAL-手动添加, CUSTOM_OUTFIT-自定义穿搭
     */
    private String source;

    /**
     * 关联product.id（仅淘宝导入）
     */
    private Long productId;

    /**
     * 标题
     */
    private String title;

    /**
     * 主图OSS objectKey
     */
    private String imageKey;

    /**
     * 详情图OSS objectKey列表(JSON数组)
     */
    private String detailImageKeys;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记：0-未删除, 1-已删除
     */
    @TableLogic
    private Integer deleted;
}
