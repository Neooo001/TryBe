-- =============================================
-- 数据库: trybe
-- 服务:   body-profile-service (身形档案服务)
-- 表名:   body_profile (身形档案主表)
-- 描述:   每个用户唯一一条身形档案，身型测量数据作为档案自身字段
-- =============================================

USE `trybe`;

DROP TABLE IF EXISTS `body_profile`;
CREATE TABLE `body_profile` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '档案ID',
    `user_id`      BIGINT       NOT NULL                COMMENT '用户ID',
    `name`         VARCHAR(128) NOT NULL DEFAULT '默认档案' COMMENT '档案名称',
    `height`       DECIMAL(5,2) DEFAULT NULL            COMMENT '身高(cm)',
    `weight`       DECIMAL(5,2) DEFAULT NULL            COMMENT '体重(kg)',
    `bust`         DECIMAL(5,2) DEFAULT NULL            COMMENT '胸围(cm)',
    `waist`        DECIMAL(5,2) DEFAULT NULL            COMMENT '腰围(cm)',
    `hip`          DECIMAL(5,2) DEFAULT NULL            COMMENT '臀围(cm)',
    `shoulder`     DECIMAL(5,2) DEFAULT NULL            COMMENT '肩宽(cm)',
    `arm`          DECIMAL(5,2) DEFAULT NULL            COMMENT '臂长(cm)',
    `leg`          DECIMAL(5,2) DEFAULT NULL            COMMENT '腿长(cm)',
    `body_type`    VARCHAR(32)  DEFAULT NULL            COMMENT '体型标签：STRAIGHT-直筒型, PEAR-梨型, APPLE-苹果型, HOURGLASS-沙漏型, INVERTED_TRIANGLE-倒三角型',
    `skin_tone`    VARCHAR(32)  DEFAULT NULL            COMMENT '肤色：冷白皮/暖白皮/自然色/小麦色/古铜色',
    `modify_parts` JSON         DEFAULT NULL            COMMENT '期望修饰部位(JSON数组)',
    `remark`       VARCHAR(512) DEFAULT NULL            COMMENT '备注',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='身形档案主表';


-- =============================================
-- 数据库: trybe
-- 服务:   body-profile-service (身形档案服务)
-- 表名:   body_profile_material (档案-素材中间表)
-- 描述:   档案 1:N 素材，形象(FACE)/手部(HAND)均通过本表关联
-- =============================================

DROP TABLE IF EXISTS `body_profile_material`;
CREATE TABLE `body_profile_material` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `profile_id`    BIGINT      NOT NULL                COMMENT '档案ID',
    `material_id`   BIGINT      NOT NULL                COMMENT '素材ID(material表)',
    `material_type` VARCHAR(16) NOT NULL                COMMENT '素材类型：FACE-人脸, HAND-手部',
    `sort_order`    INT         NOT NULL DEFAULT 0      COMMENT '排序',
    `is_primary`    TINYINT     NOT NULL DEFAULT 0      COMMENT '是否主图：0-否, 1-是',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_profile_material` (`profile_id`, `material_id`),
    KEY `idx_profile_type` (`profile_id`, `material_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='档案-素材中间表';
