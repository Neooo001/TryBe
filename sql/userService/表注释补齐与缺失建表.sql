-- ============================================================
-- TryBe · 表/字段中文注释补齐 + 缺失表补建
--
-- 背景：双账号域拆分后新表 web_user / web_user_archive 建表时用的是英文注释，
--       且部分字段无注释；同时 user → wx_user 改名后，旧表里「指向 user.id」
--       之类注释已过时。本脚本统一校正。
--
-- 另：核查发现 3 张「代码在用、库中却没有」的表（product / creator_item /
--     creator_item_model_image），一并在第 4 节补建。
--
-- 说明：ALTER TABLE ... MODIFY COLUMN 必须重复完整的列定义（类型/非空/默认值），
--       否则会丢失属性，故下面每行都写全。
-- ============================================================

-- ============ 1. web_user：Web 域用户表 ============

ALTER TABLE `web_user`
  MODIFY COLUMN `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID（web域主键；与 wx_user.id 无任何关联，两域ID各自独立编号）',
  MODIFY COLUMN `phone`           VARCHAR(20)  NOT NULL COMMENT '登录手机号（web域的账号标识，唯一键 uk_phone）',
  MODIFY COLUMN `password_hash`   VARCHAR(100) NOT NULL COMMENT '登录密码 BCrypt 哈希（明文不落库）',
  MODIFY COLUMN `nickname`        VARCHAR(64)  NOT NULL COMMENT '用户昵称（注册未填时默认「小Try」）',
  MODIFY COLUMN `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '账号状态：0-冻结, 1-正常',
  MODIFY COLUMN `last_login_time` DATETIME     NULL COMMENT '最近登录时间（沉睡归档判定依据：≥180天未登录，该手机号可被新注册者接手，原账号整行归档）',
  MODIFY COLUMN `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  COMMENT = 'Web域用户表（创作者后台账号；与小程序 wx_user 完全独立、零交集）';

-- ============ 2. web_user_archive：Web 域沉睡账号归档表 ============

ALTER TABLE `web_user_archive`
  MODIFY COLUMN `id`              BIGINT       NOT NULL COMMENT '原 web_user 表主键（沿用原ID，便于回溯与人工还原）',
  MODIFY COLUMN `phone`           VARCHAR(20)  NOT NULL COMMENT '被释放的手机号（新注册者将占用此号）',
  MODIFY COLUMN `password_hash`   VARCHAR(100) NOT NULL COMMENT '归档时的密码哈希（仅留档，不可用于登录）',
  MODIFY COLUMN `nickname`        VARCHAR(64)  NOT NULL COMMENT '归档时的昵称',
  MODIFY COLUMN `status`          TINYINT      NOT NULL COMMENT '归档时的账号状态：0-冻结, 1-正常',
  MODIFY COLUMN `last_login_time` DATETIME     NULL COMMENT '归档时的最近登录时间',
  MODIFY COLUMN `create_time`     DATETIME     NOT NULL COMMENT '原账号创建时间',
  MODIFY COLUMN `archive_reason`  VARCHAR(32)  NOT NULL DEFAULT 'SLEEP_RELEASE' COMMENT '归档原因：SLEEP_RELEASE-沉睡释放（≥180天未登录）',
  MODIFY COLUMN `archived_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
  COMMENT = 'Web域沉睡账号归档表（手机号被新注册者接手时，原账号整行从 web_user 搬迁至此，可回溯、可人工还原）';

-- ============ 3. 修正因 user → wx_user 改名而过时的注释 ============

ALTER TABLE `wx_user`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID（wx域主键；与 web_user.id 无任何关联，两域ID各自独立编号）',
  COMMENT = '小程序用户表（wx域；与创作者后台 web_user 完全独立、零交集，微信openid为身份标识，手机号仅为可选便利凭据）';

ALTER TABLE `user_identity`
  MODIFY COLUMN `user_id` BIGINT NOT NULL COMMENT '所属用户ID（指向 wx_user.id，小程序域）';

ALTER TABLE `user_password`
  MODIFY COLUMN `user_id` BIGINT NOT NULL COMMENT '用户ID（1:1 指向 wx_user.id，小程序域）';

-- ============ 4. 补建「代码在用、库中缺失」的 3 张表 ============

-- 4.1 淘宝商品表（wardrobe-service：淘宝链接导入，num_iid 去重）
--     对应 PO：wardrobe-service/.../domain/po/Product.java
CREATE TABLE IF NOT EXISTS `product` (
  `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `num_iid`           BIGINT        NULL COMMENT '淘宝商品数字ID（导入去重键）',
  `title`             VARCHAR(512)  NULL COMMENT '商品标题',
  `pict_url`          VARCHAR(1024) NULL COMMENT '原始主图URL（淘宝CDN）',
  `small_images`      TEXT          NULL COMMENT '原始小图URL列表（JSON数组）',
  `reserve_price`     VARCHAR(32)   NULL COMMENT '原价（字符串，避免精度丢失）',
  `zk_final_price`    VARCHAR(32)   NULL COMMENT '折后价/券后价',
  `item_url`          VARCHAR(1024) NULL COMMENT '商品落地页URL',
  `seller_id`         VARCHAR(64)   NULL COMMENT '卖家ID',
  `shop_title`        VARCHAR(256)  NULL COMMENT '店铺名称',
  `volume`            VARCHAR(32)   NULL COMMENT '30天销量',
  `provcity`          VARCHAR(64)   NULL COMMENT '发货地（省+市）',
  `category`          VARCHAR(128)  NULL COMMENT '商品类目',
  `main_image_key`    VARCHAR(512)  NULL COMMENT '转存后的OSS主图 objectKey',
  `detail_image_keys` TEXT          NULL COMMENT '转存后的OSS详情图 objectKey 列表（JSON数组）',
  `raw_json`          TEXT          NULL COMMENT '淘宝API原始返回JSON（备用/回溯）',
  `create_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`           TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_num_iid` (`num_iid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='淘宝商品表（导入公共资源，按 num_iid 去重）';

-- 4.2 创作者物品表（creator-service 商品池）
--     对应 PO：creator-service/.../domain/po/CreatorItem.java
CREATE TABLE IF NOT EXISTS `creator_item` (
  `id`              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `item_no`         VARCHAR(32)    NOT NULL COMMENT '物品业务编号，如 CI20250101000001（全局唯一）',
  `creator_id`      BIGINT         NOT NULL COMMENT '创作者用户ID（web域，指向 web_user.id）',
  `category_code`   VARCHAR(32)    NULL COMMENT '一级分类：TOP/BOTTOM/SET/SHOES/BAG/ACCESSORY/DECOR/OTHER',
  `title`           VARCHAR(256)   NULL COMMENT '物品标题',
  `description`     VARCHAR(2048)  NULL COMMENT '物品描述',
  `price`           DECIMAL(10,2)  NULL COMMENT '价格（元）',
  `status`          VARCHAR(16)    NULL COMMENT '状态：DRAFT-草稿, ON_SHELF-已上架, OFF_SHELF-已下架',
  `tryon_mode`      VARCHAR(16)    NULL COMMENT '试穿模式：FLAT-平铺, MODEL-模特, BOTH-两者',
  `color_hex`       VARCHAR(16)    NULL COMMENT '主色 HEX，如 #RRGGBB',
  `material`        VARCHAR(64)    NULL COMMENT '材质',
  `pattern_tags`    VARCHAR(512)   NULL COMMENT '图案/风格标签（JSON数组字符串）',
  `image_key`       VARCHAR(512)   NULL COMMENT '主图 OSS objectKey（白底/平铺展示图）',
  `cutout_png_key`  VARCHAR(512)   NULL COMMENT '透明底 PNG OSS objectKey（试穿素材）',
  `reference_keys`  TEXT           NULL COMMENT '参考图 objectKey 列表（JSON数组）',
  `real_width_mm`   INT            NULL COMMENT '实物宽度（毫米）',
  `real_height_mm`  INT            NULL COMMENT '实物高度（毫米）',
  `px_per_mm`       DECIMAL(10,4)  NULL COMMENT '尺寸标定结果：每毫米像素数（前端 Canvas 标定后回传）',
  `anchor_json`     TEXT           NULL COMMENT '锚点标注JSON，如 {"neck":[x,y],"shoulderL":[x,y]}',
  `demo_model_id`   BIGINT         NULL COMMENT '演示模特ID（预留字段，v1 不启用，属 tryon-service）',
  `create_time`     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item_no` (`item_no`),
  KEY `idx_creator_id` (`creator_id`),
  KEY `idx_creator_status` (`creator_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='创作者物品表（创作者商品池，与 wardrobe_item / product 无外键关联）';

-- 4.3 创作者物品模特图表（模特上身图 + 自动质检 + 肖像授权）
--     对应 PO：creator-service/.../domain/po/CreatorItemModelImage.java
CREATE TABLE IF NOT EXISTS `creator_item_model_image` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `item_id`            BIGINT       NOT NULL COMMENT '所属物品ID（指向 creator_item.id）',
  `creator_id`         BIGINT       NOT NULL COMMENT '创作者用户ID（冗余，便于按人查询）',
  `image_key`          VARCHAR(512) NULL COMMENT '模特图 OSS objectKey',
  `sort_order`         INT          NOT NULL DEFAULT 0 COMMENT '展示顺序，越小越靠前',
  `width`              INT          NULL COMMENT '图片宽（px，质检时写入）',
  `height`             INT          NULL COMMENT '图片高（px，质检时写入）',
  `qc_status`          VARCHAR(16)  NULL COMMENT '质检状态：PENDING-待检, PASS-通过, WARN-警告, FAIL-失败',
  `qc_detail`          TEXT         NULL COMMENT '质检明细JSON，如 {"faceCount":1,"minSizeOk":true}',
  `auth_confirmed`     TINYINT      NOT NULL DEFAULT 0 COMMENT '创作者已勾选肖像授权声明：0-否, 1-是',
  `auth_version`       VARCHAR(16)  NULL COMMENT '授权声明文本版本号，如 v1.0',
  `auth_confirmed_at`  DATETIME     NULL COMMENT '授权确认时间',
  `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_item_id` (`item_id`),
  KEY `idx_creator_id` (`creator_id`),
  KEY `idx_qc_status` (`qc_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='创作者物品模特图表（模特上身图 + 自动质检结果 + 肖像授权记录）';

-- ============ 5. 验证 ============

SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'trybe'
ORDER BY TABLE_NAME;

SELECT TABLE_NAME, COLUMN_NAME, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'trybe'
  AND TABLE_NAME IN ('web_user', 'web_user_archive')
ORDER BY TABLE_NAME, ORDINAL_POSITION;
