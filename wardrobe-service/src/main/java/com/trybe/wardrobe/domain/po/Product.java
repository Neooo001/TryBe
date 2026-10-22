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
 * 商品表（历史遗留：淘宝链接导入已下线，此表仅用于兼容存量数据，不再写入）
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("product")
public class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 商品数字ID（历史字段）
     */
    private Long numIid;

    /**
     * 商品标题
     */
    private String title;

    /**
     * 原始主图URL
     */
    private String pictUrl;

    /**
     * 原始小图URL列表(JSON数组)
     */
    private String smallImages;

    /**
     * 原价
     */
    private String reservePrice;

    /**
     * 折后价/券后价
     */
    private String zkFinalPrice;

    /**
     * 商品落地页URL
     */
    private String itemUrl;

    /**
     * 卖家ID
     */
    private String sellerId;

    /**
     * 店铺名称
     */
    private String shopTitle;

    /**
     * 30天销量
     */
    private String volume;

    /**
     * 发货地
     */
    private String provcity;

    /**
     * 商品类目
     */
    private String category;

    /**
     * OSS主图objectKey
     */
    private String mainImageKey;

    /**
     * OSS详情图objectKey列表(JSON数组)
     */
    private String detailImageKeys;

    /**
     * 第三方接口原始返回JSON（历史字段）
     */
    private String rawJson;

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