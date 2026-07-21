-- =============================================
-- 数据库: trybe
-- 变更:   用户表增加个性签名和性别字段
-- 描述:   支持编辑资料功能中的个性签名和性别
-- =============================================

USE `trybe`;

ALTER TABLE `user`
    ADD COLUMN `bio`    VARCHAR(500) DEFAULT NULL COMMENT '个性签名' AFTER `nickname`,
    ADD COLUMN `gender` TINYINT      DEFAULT 0    COMMENT '性别（0未知 1男 2女）' AFTER `bio`;