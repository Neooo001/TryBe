-- =============================================
-- 数据库: trybe
-- 服务:   wardrobe-service (衣柜服务)
-- 描述:   ① wardrobe_item 衣服属性扩展（AI 识别字段 + 推荐扩展字段）
--         ② wardrobe_category 用户自定义分类树
--         ③ wardrobe_import_task 衣服导入异步任务
-- 依赖:   需先执行「创建衣柜表.sql」
-- =============================================

USE `trybe`;

-- =============================================
-- ① wardrobe_item 扩展
-- 说明: 原表只承载「来源 + 标题 + 图片」，本脚本补齐
--       AI 识别结果字段、用户可编辑字段、以及后期智能推荐所需的属性字段
-- =============================================

ALTER TABLE `wardrobe_item`
    -- ---------- 图片 ----------
    ADD COLUMN `origin_image_key`  VARCHAR(512)  DEFAULT NULL COMMENT '原图OSS objectKey（用户上传原图，AI识别前）' AFTER `image_key`,
    ADD COLUMN `cutout_image_key`  VARCHAR(512)  DEFAULT NULL COMMENT 'AI裁剪图OSS objectKey（image_key 为其镜像，存裁剪结果）' AFTER `origin_image_key`,

    -- ---------- AI 识别基础字段 ----------
    ADD COLUMN `category_l1`       VARCHAR(32)   DEFAULT NULL COMMENT '一级分类：TOP-上装, BOTTOM-下装, SET-套装, SHOES-鞋子, BAG-包包, ACCESSORY-首饰, DECOR-配饰, OTHER-未分类' AFTER `cutout_image_key`,
    ADD COLUMN `category_l2`       VARCHAR(64)   DEFAULT NULL COMMENT '二级分类：外套/短袖/卫衣/毛衣/背心/打底/长裤/短裤/长裙/短裙/打底裤/连衣裙/连裤/运动鞋/.../未分类',
    ADD COLUMN `category_id`       BIGINT        DEFAULT NULL COMMENT '关联用户自定义分类节点 wardrobe_category.id（用户手动归类时写入）',
    ADD COLUMN `color`             VARCHAR(32)   DEFAULT NULL COMMENT '主色（中文，如：灰色）',
    ADD COLUMN `color_hex`         VARCHAR(16)   DEFAULT NULL COMMENT '主色HEX值（如 #6E6E6E，供前端色块渲染）',
    ADD COLUMN `colors`            VARCHAR(256)  DEFAULT NULL COMMENT '颜色列表(JSON数组，如 ["灰色","白色"])',
    ADD COLUMN `seasons`           VARCHAR(64)   DEFAULT NULL COMMENT '适用季节，逗号分隔：SPRING,SUMMER,AUTUMN,WINTER',
    ADD COLUMN `wear_status`       VARCHAR(16)   NOT NULL DEFAULT 'WEARING' COMMENT '穿着状态：WEARING-在穿, WISHLIST-种草, IDLE-闲置, DISCARD-舍弃',

    -- ---------- 用户可编辑 / 购物信息 ----------
    ADD COLUMN `brand`             VARCHAR(128)  DEFAULT NULL COMMENT '品牌/店铺',
    ADD COLUMN `price`             DECIMAL(10,2) DEFAULT NULL COMMENT '价格(元)',
    ADD COLUMN `wear_count`        INT           NOT NULL DEFAULT 0 COMMENT '穿着次数',
    ADD COLUMN `cost_per_wear`     DECIMAL(10,2) DEFAULT NULL COMMENT '单次穿着成本 = price / wear_count（wear_count>0 时由服务端计算）',
    ADD COLUMN `purchase_date`     DATE          DEFAULT NULL COMMENT '购买日期',

    -- ---------- 智能推荐扩展字段 ----------
    ADD COLUMN `style`             VARCHAR(64)   DEFAULT NULL COMMENT '主风格（如：简约通勤）',
    ADD COLUMN `styles`            VARCHAR(256)  DEFAULT NULL COMMENT '风格标签列表(JSON数组)',
    ADD COLUMN `material`          VARCHAR(64)   DEFAULT NULL COMMENT '材质（如：棉质 / 聚酯纤维 / 羊毛）',
    ADD COLUMN `occasions`         VARCHAR(256)  DEFAULT NULL COMMENT '适用场合列表(JSON数组，如 ["通勤","日常"])',
    ADD COLUMN `size`              VARCHAR(32)   DEFAULT NULL COMMENT '尺码（如：M / 170/88A / 40）',
    ADD COLUMN `fit`               VARCHAR(32)   DEFAULT NULL COMMENT '版型（如：直筒 / 修身 / 宽松 / 廓形）',
    ADD COLUMN `pattern`           VARCHAR(32)   DEFAULT NULL COMMENT '图案（如：纯色 / 条纹 / 格纹 / 印花）',
    ADD COLUMN `thickness`         VARCHAR(16)   DEFAULT NULL COMMENT '厚薄（THIN-薄, MEDIUM-适中, THICK-厚）',
    ADD COLUMN `warmth`            TINYINT       DEFAULT NULL COMMENT '保暖度 1-5（AI推荐排序因子）',
    ADD COLUMN `formality`         TINYINT       DEFAULT NULL COMMENT '正式度 1-5（1=运动休闲, 5=正式礼服，AI推荐排序因子）',
    ADD COLUMN `tags`              VARCHAR(512)  DEFAULT NULL COMMENT '自由标签(JSON数组)',

    -- ---------- AI 处理元信息 ----------
    ADD COLUMN `ai_recognized`     TINYINT       NOT NULL DEFAULT 0 COMMENT 'AI识别是否完成：0-未识别/失败, 1-已识别',
    ADD COLUMN `ai_model`          VARCHAR(64)   DEFAULT NULL COMMENT '执行识别的模型标识（如 qwen-vl-max）',
    ADD COLUMN `ai_confidence`     DECIMAL(5,4)  DEFAULT NULL COMMENT 'AI识别置信度 0-1',
    ADD COLUMN `ai_raw_json`       TEXT          DEFAULT NULL COMMENT 'AI原始返回JSON（调试与回溯用）';

-- 索引：衣橱页按分类 Tab / 状态筛选
ALTER TABLE `wardrobe_item`
    ADD INDEX `idx_user_category` (`user_id`, `category_l1`, `wear_status`),
    ADD INDEX `idx_user_wear_status` (`user_id`, `wear_status`);


-- =============================================
-- ② wardrobe_category (用户自定义分类树)
-- 说明: 两级分类，支持重命名 / 排序 / 新增 / 删除。
--       用户首次访问分类接口时由服务端按默认模板懒初始化，
--       is_system=1 的节点不允许删除（仅可重命名与排序）。
-- =============================================

DROP TABLE IF EXISTS `wardrobe_category`;
CREATE TABLE `wardrobe_category` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `user_id`       BIGINT       NOT NULL                COMMENT '所属用户ID',
    `parent_id`     BIGINT       NOT NULL DEFAULT 0      COMMENT '父分类ID，0 表示一级分类',
    `level`         TINYINT      NOT NULL DEFAULT 1      COMMENT '层级：1-一级分类, 2-二级分类',
    `cat_code`      VARCHAR(32)  DEFAULT NULL            COMMENT '一级分类业务码（TOP/BOTTOM/SET/SHOES/BAG/ACCESSORY/DECOR/OTHER），二级分类为 NULL',
    `name`          VARCHAR(64)  NOT NULL                COMMENT '分类名称',
    `sort_no`       INT          NOT NULL DEFAULT 0      COMMENT '排序号，越小越靠前（前端拖拽排序写回）',
    `is_system`     TINYINT      NOT NULL DEFAULT 0      COMMENT '是否系统内置：0-用户自建, 1-系统模板（不可删除）',
    `deleted`       TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0-未删除, 1-已删除',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_parent` (`user_id`, `parent_id`, `sort_no`),
    KEY `idx_user_level` (`user_id`, `level`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户自定义衣服分类树';


-- =============================================
-- ③ wardrobe_import_task (衣服导入异步任务)
-- 说明: 一次「导入衣服」= 一条任务记录。
--       上传即落库(PENDING) → MQ 异步处理(PROCESSING) → 识别+裁剪完成(SUCCESS/FAILED)
--       前端凭 task_id 轮询进度，任务携带 AI 识别结果与产物图片
-- =============================================

DROP TABLE IF EXISTS `wardrobe_import_task`;
CREATE TABLE `wardrobe_import_task` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `task_no`            VARCHAR(40)  NOT NULL                COMMENT '任务业务号（雪花ID字符串，同时作为OSS目录名，全局唯一）',
    `user_id`            BIGINT       NOT NULL                COMMENT '用户ID',
    `source`             VARCHAR(32)  NOT NULL DEFAULT 'AI_IMPORT' COMMENT '导入来源：AI_IMPORT-AI识别导入, MANUAL-手动添加, TAOBAO_IMPORT-淘宝导入',
    `origin_object_key`  VARCHAR(512) NOT NULL                COMMENT '原图OSS objectKey',
    `cutout_object_key`  VARCHAR(512) DEFAULT NULL            COMMENT 'AI裁剪图OSS objectKey（处理成功后有值）',
    `status`             TINYINT      NOT NULL DEFAULT 0      COMMENT '任务状态：0-待处理, 1-处理中, 2-成功, 3-失败',
    `wardrobe_id`        BIGINT       DEFAULT NULL            COMMENT '生成的衣柜条目ID（草稿，用户确认/编辑后转正式）',
    `ai_model`           VARCHAR(64)  DEFAULT NULL            COMMENT '本次使用的识别模型',
    `ai_result_json`     TEXT         DEFAULT NULL            COMMENT 'AI识别结果JSON（结构化属性）',
    `ai_raw_json`        TEXT         DEFAULT NULL            COMMENT 'AI原始返回JSON',
    `fail_reason`        VARCHAR(1024) DEFAULT NULL           COMMENT '失败原因',
    `retry_count`        INT          NOT NULL DEFAULT 0      COMMENT '重试次数',
    `cost_ms`            BIGINT       DEFAULT NULL            COMMENT '处理耗时(毫秒)',
    `start_time`         DATETIME     DEFAULT NULL            COMMENT '开始处理时间',
    `end_time`           DATETIME     DEFAULT NULL            COMMENT '处理结束时间',
    `deleted`            TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0-未删除, 1-已删除',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_no` (`task_no`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_wardrobe_id` (`wardrobe_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='衣服导入异步任务表';
