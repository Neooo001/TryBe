-- =============================================================
-- TryBe 统一账号体系改造脚本
--   内容：手机号唯一索引 + user_identity 凭据表 + 数据迁移
--         + unionid / 合并墓碑预留 + 账号合并日志
-- 目标库：trybe（数据库在虚拟机 192.168.100.128）
-- 说明：**只交付，不代执行**。可直接用 Navicat / DataGrip 运行。
--       未使用 DELIMITER；已带 USE trybe; 防改错库。
-- 执行顺序：Part 0 → Part 5 严格按序，Part 1 失败会阻断后续。
-- 幂等性：Part 2 / Part 3 / Part 5 可重复执行；Part 1 / Part 4 重复执行会报错，可忽略。
-- =============================================================

USE trybe;


-- =============================================================
-- Part 0  前置体检（必须先跑，全为 0 行才能继续）
-- =============================================================

-- 0.1 重复手机号。返回 0 行是 Part 1 能加唯一索引的前提。
SELECT phone, COUNT(*) AS cnt, GROUP_CONCAT(id) AS user_ids
FROM `user`
WHERE phone IS NOT NULL AND phone <> ''
GROUP BY phone
HAVING cnt > 1;

-- 0.2 一人多账号嫌疑（同一手机号挂在不同账号上，即本次要解决的核心问题）
SELECT phone, COUNT(DISTINCT user_id) AS user_cnt, GROUP_CONCAT(user_id) AS user_ids
FROM `user_identity`
WHERE identity_type = 'PHONE'
GROUP BY phone
HAVING user_cnt > 1;
-- 注：首次执行时 user_identity 尚不存在，这条会报错，属正常，跑完 Part 2 之后再回来执行。


-- =============================================================
-- Part 1  手机号唯一索引（止血：从 DB 层杜绝并发写重）
-- =============================================================

-- 1.1 删掉旧的普通索引（不存在会报错，可忽略继续）
ALTER TABLE `user` DROP INDEX `idx_phone`;

-- 1.2 建唯一索引
--     MySQL 唯一索引允许多个 NULL，因此"未绑定手机号的账号"不受影响，
--     只有真实手机号被强制唯一。改完 Java 侧应捕获 DuplicateKeyException。
ALTER TABLE `user` ADD UNIQUE KEY `uk_phone` (`phone`);


-- =============================================================
-- Part 2  新增 user_identity 凭据表（User 1 : N Identity）
-- =============================================================

CREATE TABLE IF NOT EXISTS `user_identity` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '凭据ID',
    `user_id`       BIGINT       NOT NULL                COMMENT '所属用户ID（指向 user.id）',
    `identity_type` VARCHAR(24)  NOT NULL                COMMENT '凭据类型：PHONE/WECHAT_OPENID/WECHAT_UNIONID/EMAIL/USERNAME',
    `app_id`        VARCHAR(64)  NOT NULL DEFAULT ''     COMMENT '应用标识：openid 必须配小程序 appid；手机号/邮箱填空串',
    `identifier`    VARCHAR(128) NOT NULL                COMMENT '凭据值：手机号 / openid / unionid / 邮箱',
    `credential`    VARCHAR(128) DEFAULT NULL            COMMENT '凭据密钥：密码 BCrypt 哈希；微信侧为 NULL',
    `verified`      TINYINT      NOT NULL DEFAULT 0      COMMENT '是否已验证：0否 1是（手机号需过短信验证）',
    `verified_time` DATETIME     DEFAULT NULL            COMMENT '验证通过时间',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_type_app_identifier` (`identity_type`, `app_id`, `identifier`),
    KEY `idx_user` (`user_id`),
    KEY `idx_identifier` (`identifier`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户登录凭据表（一个用户 N 条凭据）';


-- =============================================================
-- Part 3  存量数据迁移（user 旧列 → user_identity）
--         用 INSERT IGNORE，可重复执行，冲突行静默跳过。
--         迁移后 user 旧列**保留不动**，作为兼容期双写字段。
-- =============================================================

-- 3.1 手机号凭据（含密码哈希）
INSERT IGNORE INTO `user_identity`
    (`user_id`, `identity_type`, `app_id`, `identifier`, `credential`, `verified`, `verified_time`)
SELECT `id`, 'PHONE', '', `phone`, `password`, 1, `create_time`
FROM `user`
WHERE `phone` IS NOT NULL AND `phone` <> '';

-- 3.2 微信 openid 凭据
--     ⚠️ 把 <小程序appid> 替换成实际 appid（如 wx1234567890abcdef）后再执行！
INSERT IGNORE INTO `user_identity`
    (`user_id`, `identity_type`, `app_id`, `identifier`, `verified`, `verified_time`)
SELECT `id`, 'WECHAT_OPENID', '<小程序appid>', `openid`, 1, `create_time`
FROM `user`
WHERE `openid` IS NOT NULL AND `openid` <> '';

-- 3.3 迁移结果核对：行数应与下面两条 SELECT 的数量级一致
SELECT 'PHONE' AS t, COUNT(*) AS migrated FROM `user_identity` WHERE identity_type = 'PHONE'
UNION ALL
SELECT 'WECHAT_OPENID', COUNT(*) FROM `user_identity` WHERE identity_type = 'WECHAT_OPENID'
UNION ALL
SELECT 'user.phone_not_null', COUNT(*) FROM `user` WHERE phone IS NOT NULL AND phone <> ''
UNION ALL
SELECT 'user.openid_not_null', COUNT(*) FROM `user` WHERE openid IS NOT NULL AND openid <> '';


-- =============================================================
-- Part 4  unionid 预留 + 账号合并墓碑
-- =============================================================

ALTER TABLE `user`
    ADD COLUMN `unionid` VARCHAR(64) DEFAULT NULL
        COMMENT '微信开放平台 unionid（需企业/个体户主体注册开放平台，个人主体拿不到，预留）' AFTER `openid`,
    ADD COLUMN `merged_to_user_id` BIGINT DEFAULT NULL
        COMMENT '若非空表示本账号已被合并到该 user_id（墓碑记录，保留 90 天便于回滚）' AFTER `role`;

-- unionid 唯一（同样允许多个 NULL）
ALTER TABLE `user` ADD UNIQUE KEY `uk_unionid` (`unionid`);


-- =============================================================
-- Part 5  账号合并日志（幂等 + 回滚依据）
-- =============================================================

CREATE TABLE IF NOT EXISTS `account_merge_log` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `master_user_id` BIGINT      NOT NULL                COMMENT '保留的主账号（通常为手机号账号）',
    `slave_user_id`  BIGINT      NOT NULL                COMMENT '被合并并废弃的账号（通常为微信账号）',
    `trigger_scene`  VARCHAR(32) NOT NULL                COMMENT '触发场景：BIND_PHONE / LOGIN_WECHAT / MANUAL',
    `asset_snapshot` JSON        DEFAULT NULL            COMMENT '迁移前各表资产行数快照，必要时用于回滚',
    `operator`       VARCHAR(64) DEFAULT NULL            COMMENT '操作者：SELF 自助 / 运营账号标识',
    `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '合并时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_slave` (`slave_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='账号合并日志（uk_slave 保证同一次合并只执行一次）';


-- =============================================================
-- Part 6  上线后核查
-- =============================================================

-- 6.1 凭据类型分布
SELECT identity_type, COUNT(*) AS cnt FROM `user_identity` GROUP BY identity_type;

-- 6.2 已绑手机号但未设密码的账号（微信建号未设密码，无法在 Web 端登录）
SELECT u.id, u.nickname, u.phone, u.role, u.create_time
FROM `user` u
WHERE u.phone IS NOT NULL
  AND (u.password IS NULL OR u.password = '')
ORDER BY u.id DESC
LIMIT 50;

-- 6.3 检查是否还有"同一手机号挂多个账号"（应为 0 行）
SELECT i.identifier AS phone, COUNT(DISTINCT i.user_id) AS user_cnt, GROUP_CONCAT(i.user_id) AS user_ids
FROM `user_identity` i
WHERE i.identity_type = 'PHONE'
GROUP BY i.identifier
HAVING user_cnt > 1;

-- 6.4 已合并账号一览（墓碑记录）
SELECT `id`, `nickname`, `openid`, `merged_to_user_id`, `update_time`
FROM `user`
WHERE `merged_to_user_id` IS NOT NULL
ORDER BY `update_time` DESC;

-- 6.5 合并操作日志
SELECT * FROM `account_merge_log` ORDER BY `id` DESC LIMIT 50;
