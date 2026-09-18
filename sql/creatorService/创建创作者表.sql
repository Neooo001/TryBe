USE trybe;

-- ============================================================
-- 创作者后台 v1 DDL（MySQL 8，库 trybe）
-- creator_application 归属 user-service，脚本集中在本目录一并交付
-- ============================================================

-- 创作者入驻申请表（归属 user-service）
CREATE TABLE `creator_application` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`         BIGINT       NOT NULL COMMENT '申请用户ID（一个用户仅一条，驳回后更新重提）',
    `shop_name`       VARCHAR(64)  NOT NULL COMMENT '店铺/品牌名',
    `shop_intro`      VARCHAR(512) NULL     COMMENT '店铺简介',
    `contact_phone`   VARCHAR(20)  NOT NULL COMMENT '联系电话',
    `license_image_key` VARCHAR(255) NULL   COMMENT '资质图 OSS objectKey（营业执照等）',
    `category_scope`  VARCHAR(128) NULL     COMMENT '经营类目，逗号分隔 GarmentCategoryL1：TOP,BOTTOM,...',
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待审/APPROVED通过/REJECTED驳回',
    `reject_reason`   VARCHAR(255) NULL     COMMENT '驳回原因',
    `reviewer_id`     BIGINT       NULL     COMMENT '审核人（运营 user.id）',
    `reviewed_at`     DATETIME     NULL     COMMENT '审核时间',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '创作者入驻申请表';

-- 创作者物品表（创作者商品池，与 wardrobe_item/product 无关联）
CREATE TABLE `creator_item` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `item_no`          VARCHAR(32)   NOT NULL COMMENT '物品业务编号，如 CI20250101000001',
    `creator_id`       BIGINT        NOT NULL COMMENT '创作者用户ID（user.id，role=CREATOR）',
    `category_code`    VARCHAR(16)   NOT NULL COMMENT '一级分类：TOP/BOTTOM/SET/SHOES/BAG/ACCESSORY/DECOR/OTHER',
    `title`            VARCHAR(64)   NOT NULL COMMENT '物品标题',
    `description`      VARCHAR(1024) NULL     COMMENT '描述',
    `price`            DECIMAL(10,2) NULL     COMMENT '价格（元）',
    `status`           VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT草稿/PENDING待审/APPROVED已通过/REJECTED已驳回/ON_SHELF已上架/OFF_SHELF已下架',
    `tryon_mode`       VARCHAR(16)   NOT NULL DEFAULT 'FLAT' COMMENT '试穿模式：FLAT平铺图/MODEL模特上身图/BOTH两者',
    `color_hex`        VARCHAR(9)    NULL     COMMENT '主色 HEX，如 #RRGGBB',
    `material`         VARCHAR(64)   NULL     COMMENT '材质',
    `pattern_tags`     VARCHAR(255)  NULL     COMMENT '图案/风格标签，JSON数组字符串',
    `image_key`        VARCHAR(255)  NULL     COMMENT '主图 OSS objectKey（白底/平铺展示图）',
    `cutout_png_key`   VARCHAR(255)  NULL     COMMENT '透明底 PNG OSS objectKey（试穿素材）',
    `reference_keys`   TEXT          NULL     COMMENT '参考图 objectKey 列表，JSON数组',
    `real_width_mm`    INT           NULL     COMMENT '实物宽度（毫米）',
    `real_height_mm`   INT           NULL     COMMENT '实物高度（毫米）',
    `px_per_mm`        DECIMAL(10,4) NULL     COMMENT '尺寸标定结果：每毫米像素数（前端 Canvas 标定后回传）',
    `anchor_json`      TEXT          NULL     COMMENT '锚点标注 JSON：{"neck":[x,y],"shoulderL":[x,y],...}（前端标注后回传）',
    `demo_model_id`    BIGINT        NULL     COMMENT '演示模特ID（预留字段，v1 不启用，属 tryon-service）',
    `reject_reason`    VARCHAR(255)  NULL     COMMENT '最近一次驳回原因',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删/1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_item_no` (`item_no`),
    KEY `idx_creator_status` (`creator_id`, `status`),
    KEY `idx_status` (`status`),
    KEY `idx_category` (`category_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '创作者物品表';

-- 商家模特图表（模特上身图 + 自动质检结果 + 肖像授权记录）
CREATE TABLE `creator_item_model_image` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `item_id`           BIGINT       NOT NULL COMMENT '所属物品 creator_item.id',
    `creator_id`        BIGINT       NOT NULL COMMENT '创作者用户ID（冗余，便于按人查询）',
    `image_key`         VARCHAR(255) NOT NULL COMMENT '模特图 OSS objectKey',
    `sort_order`        INT          NOT NULL DEFAULT 0 COMMENT '展示顺序',
    `width`             INT          NULL     COMMENT '图片宽（px，质检时写入）',
    `height`            INT          NULL     COMMENT '图片高（px，质检时写入）',
    `qc_status`         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '质检状态：PENDING待检/PASS通过/WARN警告可用/FAIL不通过',
    `qc_detail`         VARCHAR(512) NULL     COMMENT '质检明细 JSON：{"faceCount":1,"minSizeOk":true,...}',
    `auth_confirmed`    TINYINT      NOT NULL DEFAULT 0 COMMENT '创作者已勾选肖像授权声明：0否/1是',
    `auth_version`      VARCHAR(16)  NULL     COMMENT '授权声明文本版本号，如 v1.0',
    `auth_confirmed_at` DATETIME     NULL     COMMENT '授权确认时间',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删/1已删',
    PRIMARY KEY (`id`),
    KEY `idx_item` (`item_id`),
    KEY `idx_creator` (`creator_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '创作者物品模特图表';

-- 审核流水表（通用，v1 仅记录 creator_item 的审核与上下架动作）
CREATE TABLE `audit_record` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `object_type` VARCHAR(32)  NOT NULL COMMENT '对象类型：CREATOR_ITEM（v1 仅此一种；入驻审核记录在 creator_application 自带字段，不写本表）',
    `object_id`   BIGINT       NOT NULL COMMENT '对象ID',
    `action`      VARCHAR(32)  NOT NULL COMMENT '动作：SUBMIT提交审核/APPROVE通过/REJECT驳回/ON_SHELF上架/OFF_SHELF下架',
    `operator_id` BIGINT       NOT NULL COMMENT '操作人 user.id',
    `reason`      VARCHAR(255) NULL     COMMENT '原因/备注',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_object` (`object_type`, `object_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审核流水表';
