-- =============================================
-- 数据库: trybe
-- 服务:   wardrobe-service (衣柜服务)
-- 描述:   商品公共资源表 + 用户衣柜条目表
-- =============================================

USE `trybe`;

-- =============================================
-- 表名: product (商品表)
-- 描述: 淘宝导入的商品公共资源，以 num_iid 唯一去重，多用户共享
-- =============================================
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '商品主键ID',
    `num_iid`           BIGINT        NOT NULL                COMMENT '淘宝商品ID',
    `title`             VARCHAR(512)  DEFAULT NULL            COMMENT '商品标题',
    `pict_url`          VARCHAR(1024) DEFAULT NULL            COMMENT '原始主图URL（淘宝CDN）',
    `small_images`      TEXT          DEFAULT NULL            COMMENT '原始小图URL列表(JSON数组)',
    `reserve_price`     VARCHAR(32)   DEFAULT NULL            COMMENT '原价',
    `zk_final_price`    VARCHAR(32)   DEFAULT NULL            COMMENT '折后价/券后价',
    `item_url`          VARCHAR(1024) DEFAULT NULL            COMMENT '商品落地页URL',
    `seller_id`         VARCHAR(64)   DEFAULT NULL            COMMENT '卖家ID',
    `shop_title`        VARCHAR(256)  DEFAULT NULL            COMMENT '店铺名称',
    `volume`            VARCHAR(32)   DEFAULT NULL            COMMENT '30天销量',
    `provcity`          VARCHAR(128)  DEFAULT NULL            COMMENT '发货地',
    `category`          VARCHAR(128)  DEFAULT NULL            COMMENT '商品类目',
    `main_image_key`    VARCHAR(512)  DEFAULT NULL            COMMENT 'OSS主图objectKey',
    `detail_image_keys` TEXT          DEFAULT NULL            COMMENT 'OSS详情图objectKey列表(JSON数组)',
    `raw_json`          TEXT          DEFAULT NULL            COMMENT '淘宝API原始返回JSON（备用）',
    `deleted`           TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0-未删除, 1-已删除',
    `create_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_num_iid` (`num_iid`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品表（淘宝导入公共资源）';


-- =============================================
-- 表名: wardrobe_item (衣柜条目表)
-- 描述: 用户衣柜，来源为 TAOBAO_IMPORT / MANUAL / CUSTOM_OUTFIT
-- =============================================
DROP TABLE IF EXISTS `wardrobe_item`;
CREATE TABLE `wardrobe_item` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '衣柜条目ID',
    `user_id`           BIGINT        NOT NULL                COMMENT '用户ID',
    `source`            VARCHAR(32)   NOT NULL                COMMENT '来源：TAOBAO_IMPORT-淘宝导入, MANUAL-手动添加, CUSTOM_OUTFIT-自定义穿搭',
    `product_id`        BIGINT        DEFAULT NULL            COMMENT '关联product.id（仅淘宝导入）',
    `title`             VARCHAR(512)  DEFAULT NULL            COMMENT '标题',
    `image_key`         VARCHAR(512)  DEFAULT NULL            COMMENT '主图OSS objectKey',
    `detail_image_keys` TEXT          DEFAULT NULL            COMMENT '详情图OSS objectKey列表(JSON数组)',
    `remark`            VARCHAR(512)  DEFAULT NULL            COMMENT '备注',
    `deleted`           TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0-未删除, 1-已删除',
    `create_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_source` (`user_id`, `source`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='衣柜条目表';