﻿﻿-- =============================================
-- 数据库: trybe
-- 表名:   user (用户表)
-- 描述:   用户服务核心表，存储用户基本信息
-- =============================================

CREATE DATABASE IF NOT EXISTS `trybe` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE `trybe`;

DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `nickname`    VARCHAR(64)  DEFAULT '小Try'         COMMENT '用户昵称',
    `avatar`      VARCHAR(512) DEFAULT NULL            COMMENT '头像OSS存储地址',
    `password`    VARCHAR(128) DEFAULT NULL            COMMENT '密码，BCrypt加密存储',
    `phone`       VARCHAR(20)  DEFAULT NULL            COMMENT '注册手机号',
    `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '使用状态（0冻结 1正常）',
    `openid`      VARCHAR(64)  DEFAULT NULL            COMMENT '微信openid',
    `bio`         VARCHAR(512) DEFAULT NULL            COMMENT '个人介绍',
    `gender`      TINYINT      DEFAULT NULL            COMMENT '性别（0未知 1男 2女）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_openid` (`openid`),
    KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';