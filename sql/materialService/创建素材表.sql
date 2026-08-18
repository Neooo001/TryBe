-- =============================================
-- 数据库: trybe
-- 服务:   material-service (素材中心服务)
-- 表名:   material (素材表)
-- 描述:   用户上传的人脸/手部原始素材，存储OSS路径与校验状态
-- =============================================

USE `trybe`;

DROP TABLE IF EXISTS `material`;
CREATE TABLE `material` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '素材ID',
    `user_id`       BIGINT       NOT NULL                COMMENT '用户ID',
    `material_type` VARCHAR(32)  NOT NULL                COMMENT '素材类型：FACE-人脸, HAND-手部',
    `object_key`    VARCHAR(512) NOT NULL DEFAULT ''     COMMENT 'OSS存储路径',
    `file_name`     VARCHAR(256) DEFAULT NULL            COMMENT '原始文件名',
    `file_size`     BIGINT       DEFAULT 0               COMMENT '文件大小(字节)',
    `content_type`  VARCHAR(64)  DEFAULT NULL            COMMENT '文件MIME类型',
    `status`        TINYINT      NOT NULL DEFAULT 0      COMMENT '校验状态：0-待上传, 1-校验中, 2-校验通过, 3-校验失败',
    `fail_reason`   VARCHAR(1024) DEFAULT NULL           COMMENT '校验失败原因',
    `deleted`       TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0-未删除, 1-已删除',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_user_type` (`user_id`, `material_type`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='素材表';


-- =============================================
-- 数据库: trybe
-- 服务:   material-service (素材中心服务)
-- 表名:   material_check_log (素材校验日志表)
-- 描述:   记录每次素材校验的详细过程，支持重试与审计追溯
-- =============================================

DROP TABLE IF EXISTS `material_check_log`;
CREATE TABLE `material_check_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `material_id`   BIGINT       NOT NULL                COMMENT '素材ID',
    `check_type`    VARCHAR(32)  NOT NULL                COMMENT '校验类型：QUALITY-质量校验, FORMAT-格式校验, AI_FACE-人脸AI校验, AI_HAND-手部AI校验',
    `status`        TINYINT      NOT NULL DEFAULT 0      COMMENT '校验结果：0-待处理, 1-处理中, 2-通过, 3-失败',
    `result_detail` TEXT         DEFAULT NULL            COMMENT '校验结果详情(JSON)',
    `cost_ms`       BIGINT       DEFAULT NULL            COMMENT '校验耗时(毫秒)',
    `start_time`    DATETIME     DEFAULT NULL            COMMENT '校验开始时间',
    `end_time`      DATETIME     DEFAULT NULL            COMMENT '校验结束时间',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_material_id` (`material_id`),
    KEY `idx_material_check` (`material_id`, `check_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='素材校验日志表';
