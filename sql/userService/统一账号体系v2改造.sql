-- =============================================================
-- TryBe 统一账号体系 v2 改造脚本
--   内容：user 表重构（抽离 openid/phone/password）
--         + user_identity 凭据表 + user_password 密码表
--         + user_phone_history 曾绑手机号表 + phone_transfer_log 审计/幂等表
-- 目标库：trybe（虚拟机 192.168.100.128）
-- 说明：只交付不代执行；已带 USE trybe;；未使用 DELIMITER；可直接 Navicat/DataGrip 运行
-- 执行顺序：Part 0 → Part 7 严格按序；Part 6（DROP 旧列）务必在**新代码上线并验证通过后**再执行
-- 幂等：Part 2 建表、Part 3 迁移（INSERT IGNORE）、Part 4 回填 可重复执行
-- =============================================================

USE trybe;

-- -------------------------------------------------------------
-- Part 0  前置体检（全为 0 行才能继续）
-- -------------------------------------------------------------

-- 0.1 重复手机号（>0 行会导致迁移后唯一约束失效，必须先人工处置）
SELECT phone, COUNT(*) AS cnt, GROUP_CONCAT(id) AS user_ids
FROM `user`
WHERE phone IS NOT NULL AND phone <> ''
GROUP BY phone
HAVING cnt > 1;

-- 0.2 一个手机号被多个账号占用（含空串视为未绑定）
SELECT phone, COUNT(*) AS cnt FROM `user`
WHERE phone IS NOT NULL AND phone <> ''
GROUP BY phone HAVING cnt > 1;

-- -------------------------------------------------------------
-- Part 1  user 表重构：新增字段（不删旧列，先加后移）
-- -------------------------------------------------------------

-- 1.1 新增手机号归属状态（PRD Q4 机制）
--     0 = NEVER_BOUND 从未绑定过手机号（小程序首登 → 硬拦截强制绑号）
--     1 = BOUND       当前已绑定手机号
--     2 = RELEASED    曾绑定过、已被释放（小程序软提示补绑，不拦截）
ALTER TABLE `user`
    ADD COLUMN `phone_state` TINYINT NOT NULL DEFAULT 0
        COMMENT '手机号归属状态：0从未绑定 1已绑定 2曾绑定被释放(待补绑)' AFTER `role`;

-- 1.2 unionid 预留（个人主体拿不到，注册开放平台后可启用）
ALTER TABLE `user`
    ADD COLUMN `unionid` VARCHAR(64) DEFAULT NULL
        COMMENT '微信开放平台 unionid（预留，不启用）' AFTER `role`;

-- unionid 唯一（亦然：MySQL 唯一索引允许多个 NULL，未绑定账号不受影响）
ALTER TABLE `user` ADD UNIQUE KEY `uk_unionid` (`unionid`);

-- -------------------------------------------------------------
-- Part 2  新增凭据表 / 密码表 / 历史表 / 审计表
-- -------------------------------------------------------------

-- 2.1 登录凭据表（User 1 : N Identity）
CREATE TABLE IF NOT EXISTS `user_identity` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '凭据ID',
    `user_id`       BIGINT       NOT NULL                COMMENT '所属用户ID（指向 user.id）',
    `identity_type` VARCHAR(24)  NOT NULL                COMMENT '凭据类型：PHONE / WECHAT_OPENID',
    `app_id`        VARCHAR(64)  NOT NULL DEFAULT ''     COMMENT '应用标识：openid 必须配小程序 appid；手机号填空串',
    `identifier`    VARCHAR(128) NOT NULL                COMMENT '凭据值：手机号 / openid',
    `verified`      TINYINT      NOT NULL DEFAULT 0      COMMENT '是否已验证：0否 1是（手机号需过短信验证）',
    `verified_time` DATETIME     DEFAULT NULL            COMMENT '验证通过时间',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_type_app_identifier` (`identity_type`, `app_id`, `identifier`),
    KEY `idx_user` (`user_id`),
    KEY `idx_identifier` (`identifier`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='用户登录凭据表（一个用户 N 条凭据；手机号唯一性由 uk_type_app_identifier 承担）';

-- 2.2 密码表（与手机号解耦：手机号易主后密码仍随「人」走，支撑恢复路径）
CREATE TABLE IF NOT EXISTS `user_password` (
    `user_id`       BIGINT       NOT NULL COMMENT '用户ID（1:1 user.id）',
    `password_hash` VARCHAR(128) NOT NULL COMMENT '密码 BCrypt 哈希',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='用户密码表（与 user_identity 分离，手机号流转不影响密码归属）';

-- 2.3 曾绑手机号历史表（手机号被释放时写入，供恢复路径匹配）
CREATE TABLE IF NOT EXISTS `user_phone_history` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`        BIGINT      NOT NULL                COMMENT '曾绑定该号的账号（即被释放的 A）',
    `phone`          VARCHAR(20) NOT NULL                COMMENT '曾绑定的手机号',
    `bound_time`     DATETIME    DEFAULT NULL            COMMENT '上次绑定该号的时间',
    `released_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '释放时间',
    `release_reason` VARCHAR(32) NOT NULL DEFAULT 'PHONE_TRANSFERRED' COMMENT '释放原因：PHONE_TRANSFERRED 被其他账号抢绑',
    PRIMARY KEY (`id`),
    KEY `idx_phone` (`phone`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='曾绑定但已释放的手机号（恢复路径 + 审计）';

-- 2.4 手机号归属转移日志（幂等键 + 审计）
CREATE TABLE IF NOT EXISTS `phone_transfer_log` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `transfer_no`  VARCHAR(64) NOT NULL                COMMENT '幂等键（服务端按 B+phone+时间窗生成）',
    `phone`        VARCHAR(20) NOT NULL                COMMENT '被转移的手机号',
    `from_user_id` BIGINT      DEFAULT NULL            COMMENT '被释放方 A（无占用者时为 NULL）',
    `to_user_id`   BIGINT      NOT NULL                COMMENT '获得方 B',
    `scene`        VARCHAR(32) NOT NULL                COMMENT '触发场景：BIND_PHONE',
    `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '转移时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_transfer_no` (`transfer_no`),
    KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='手机号归属转移日志（uk_transfer_no 保证同一请求不重复执行）';

-- -------------------------------------------------------------
-- Part 3  存量数据迁移（user 旧列 → 新表；INSERT IGNORE 可重复执行）
-- -------------------------------------------------------------

-- 3.1 手机号凭据
INSERT IGNORE INTO `user_identity`
    (`user_id`, `identity_type`, `app_id`, `identifier`, `verified`, `verified_time`)
SELECT `id`, 'PHONE', '', `phone`, 1, `create_time`
FROM `user`
WHERE `phone` IS NOT NULL AND `phone` <> '';

-- 3.2 微信 openid 凭据
--     ⚠️ 执行前把 '<小程序appid>' 替换为实际 appid（如 wx1234567890abcdef）！
INSERT IGNORE INTO `user_identity`
    (`user_id`, `identity_type`, `app_id`, `identifier`, `verified`, `verified_time`)
SELECT `id`, 'WECHAT_OPENID', '<小程序appid>', `openid`, 1, `create_time`
FROM `user`
WHERE `openid` IS NOT NULL AND `openid` <> '';

-- 3.3 密码
INSERT IGNORE INTO `user_password` (`user_id`, `password_hash`, `create_time`)
SELECT `id`, `password`, `create_time`
FROM `user`
WHERE `password` IS NOT NULL AND `password` <> '';

-- 3.4 迁移核对（数量级应与源列一致）
SELECT 'PHONE' AS t, COUNT(*) AS migrated FROM `user_identity` WHERE identity_type = 'PHONE'
UNION ALL SELECT 'WECHAT_OPENID', COUNT(*) FROM `user_identity` WHERE identity_type = 'WECHAT_OPENID'
UNION ALL SELECT 'PASSWORD', COUNT(*) FROM `user_password`
UNION ALL SELECT 'user.phone_not_null', COUNT(*) FROM `user` WHERE phone IS NOT NULL AND phone <> ''
UNION ALL SELECT 'user.openid_not_null', COUNT(*) FROM `user` WHERE openid IS NOT NULL AND openid <> '';

-- -------------------------------------------------------------
-- Part 4  回填手机号归属状态
--   有手机号 → BOUND(1)；无手机号 → NEVER_BOUND(0)
--   （RELEASED(2) 只会在 Part 5 之后的运行时转移中产生，存量不会出现）
-- -------------------------------------------------------------

UPDATE `user`
SET `phone_state` = CASE
    WHEN `phone` IS NOT NULL AND `phone` <> '' THEN 1
    ELSE 0
END;

-- -------------------------------------------------------------
-- Part 5  唯一约束与索引核查
--   手机号唯一性由 user_identity.uk_type_app_identifier 承担
--   （主理人已裁决：phone 不在 user 表保留，避免双写漂移）
-- -------------------------------------------------------------

-- 5.1 核查：是否仍存在「一个手机号挂多个账号」（应为 0 行）
SELECT i.identifier AS phone, COUNT(DISTINCT i.user_id) AS user_cnt, GROUP_CONCAT(i.user_id) AS user_ids
FROM `user_identity` i
WHERE i.identity_type = 'PHONE'
GROUP BY i.identifier
HAVING user_cnt > 1;

-- -------------------------------------------------------------
-- Part 6  清理旧列（⚠️ 必须在**新代码上线并验证通过后**再执行！）
--   跨模块影响已检索完毕：
--     · 非 user-service 模块无 mapper XML；仅 creator-service/UserReadMapper 读 user 表，
--       且只查 role/nickname/id/count，不碰凭据列 → DROP 对其它模块无代码影响。
--     · 唯一漏网点：sql/userService/初始化运营管理员账号.sql 读了 user.phone → 已改走 user_identity。
-- -------------------------------------------------------------

-- ⚠️⚠️ 以下 Part 6 语句必须在「新代码上线并验证通过后」再执行 ⚠️⚠️
ALTER TABLE `user` DROP INDEX `uk_openid`;      -- 若不存在会报错，可忽略
ALTER TABLE `user` DROP INDEX `idx_phone`;      -- 若不存在会报错，可忽略
ALTER TABLE `user` DROP COLUMN `openid`;
ALTER TABLE `user` DROP COLUMN `phone`;
ALTER TABLE `user` DROP COLUMN `password`;

-- -------------------------------------------------------------
-- Part 7  上线后核查
-- -------------------------------------------------------------

-- 7.1 凭据类型分布
SELECT identity_type, COUNT(*) AS cnt FROM `user_identity` GROUP BY identity_type;

-- 7.2 待补绑账号（phone_state=2）
SELECT u.id, u.nickname, u.role, u.phone_state, u.update_time
FROM `user` u WHERE u.phone_state = 2 ORDER BY u.update_time DESC;

-- 7.3 从未绑定手机号的小程序账号（将触发硬拦截）
SELECT u.id, u.nickname, u.phone_state, i.identifier AS openid
FROM `user` u JOIN `user_identity` i ON i.user_id = u.id AND i.identity_type = 'WECHAT_OPENID'
WHERE u.phone_state = 0;

-- 7.4 手机号转移日志
SELECT * FROM `phone_transfer_log` ORDER BY `id` DESC LIMIT 50;
