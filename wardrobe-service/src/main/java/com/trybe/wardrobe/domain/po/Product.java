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
 * 商品表（淘宝导入公共资源，num_iid 唯一去重）
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
     * 淘宝商品ID
     */
    private Long numIid;

    /**
     * 商品标题
     */
    private String title;

    /**
     * 原始主图URL（淘宝CDN）
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
     * 淘宝API原始返回JSON（备用）
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