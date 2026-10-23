-- =====================================================================
-- TryBe 统一账号体系 v3（2026-10-23）
--   目标：取消「创作者 / 普通用户」双账号域，一人一号，手机号为唯一身份锚点
--   来源：wx_user（小程序域） + web_user（创作者后台域） → app_user（统一）
--
--   执行顺序不可调换，执行前请确认已备份库。
--   迁移完成后旧表改名为 *_bak_20261023 保留，不做 DROP。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 统一账号主体表
--    只放「人」的属性，不承载任何登录凭据：
--      手机号 → user_identity(identity_type='PHONE')
--      openid → user_identity(identity_type='WECHAT_OPENID')
--      密码   → user_password(user_id)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `app_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID（统一账号主键，两端共用同一套编号）',
  `nickname` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '小Try' COMMENT '昵称（Web 首次注册时随机生成，用户可改）',
  `avatar` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '头像 OSS objectKey',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '账号状态：0冻结 1正常 2已合并（并入其他账号，仅留审计）',
  `unionid` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '微信开放平台 unionid（预留）',
  `phone_state` tinyint NOT NULL DEFAULT '0' COMMENT '手机号归属状态：0从未绑定 1已绑定 2曾绑定被释放',
  `bio` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '个人介绍',
  `gender` tinyint DEFAULT NULL COMMENT '性别（0未知 1男 2女）',
  `register_source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'WX' COMMENT '首次注册端：WX小程序 / WEB创作者后台（手机号归属哪一端，账号数据即归属该端）',
  `last_login_time` datetime DEFAULT NULL COMMENT '最近登录时间（沉睡 180 天判定依据：超期后手机号可被新用户接管）',
  `merged_into_user_id` bigint DEFAULT NULL COMMENT '合并目标账号ID（status=2 时有效，仅审计用，不参与登录）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_unionid` (`unionid`),
  KEY `idx_last_login` (`last_login_time`),
  KEY `idx_merged` (`merged_into_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一用户表（不再区分创作者与普通用户；手机号唯一锚点）';

-- ---------------------------------------------------------------------
-- 2. wx_user → app_user（保留原 id，小程序侧数据零变动）
-- ---------------------------------------------------------------------
INSERT INTO `app_user`
  (`id`, `nickname`, `avatar`, `status`, `unionid`, `phone_state`, `bio`, `gender`,
   `register_source`, `last_login_time`, `create_time`, `update_time`)
SELECT
  u.`id`, u.`nickname`, u.`avatar`, u.`status`, u.`unionid`, u.`phone_state`, u.`bio`, u.`gender`,
  'WX', u.`update_time`, u.`create_time`, u.`update_time`
FROM `wx_user` u
WHERE NOT EXISTS (SELECT 1 FROM `app_user` a WHERE a.`id` = u.`id`);

-- ---------------------------------------------------------------------
-- 3. web_user 已绑手机号（user_identity 里已有 PHONE）→ 并入已有账号
--    规则：手机号已在小程序端绑定 → 数据归小程序端，只迁密码与登录时间
-- ---------------------------------------------------------------------
UPDATE `app_user` u
JOIN `user_identity` i
  ON i.`user_id` = u.`id` AND i.`identity_type` = 'PHONE' AND i.`app_id` = ''
JOIN `web_user` w
  ON w.`phone` COLLATE utf8mb4_general_ci = i.`identifier`
SET u.`last_login_time` = COALESCE(w.`last_login_time`, u.`last_login_time`),
    u.`update_time` = GREATEST(u.`update_time`, w.`update_time`);

INSERT INTO `user_password` (`user_id`, `password_hash`)
SELECT i.`user_id`, w.`password_hash`
FROM `web_user` w
JOIN `user_identity` i
  ON i.`identity_type` = 'PHONE' AND i.`app_id` = '' AND i.`identifier` = w.`phone` COLLATE utf8mb4_general_ci
ON DUPLICATE KEY UPDATE `password_hash` = VALUES(`password_hash`);

-- ---------------------------------------------------------------------
-- 4. web_user 手机号尚未在统一账号中出现 → 新建账号（归 WEB 端）
--    id 规则：1000 + web_user.id，一次性迁移避免与小程序自增 id 冲突
-- ---------------------------------------------------------------------
INSERT INTO `app_user`
  (`id`, `nickname`, `status`, `phone_state`, `register_source`, `last_login_time`, `create_time`, `update_time`)
SELECT 1000 + w.`id`, w.`nickname`, w.`status`, 1, 'WEB',
       w.`last_login_time`, w.`create_time`, w.`update_time`
FROM `web_user` w
LEFT JOIN `user_identity` i
  ON i.`identity_type` = 'PHONE' AND i.`app_id` = '' AND i.`identifier` = w.`phone` COLLATE utf8mb4_general_ci
WHERE i.`id` IS NULL
  AND NOT EXISTS (SELECT 1 FROM `app_user` a WHERE a.`id` = 1000 + w.`id`);

INSERT INTO `user_identity`
  (`user_id`, `identity_type`, `app_id`, `identifier`, `verified`, `verified_time`, `create_time`, `update_time`)
SELECT 1000 + w.`id`, 'PHONE', '', w.`phone`, 1, NOW(), w.`create_time`, NOW()
FROM `web_user` w
LEFT JOIN `user_identity` i
  ON i.`identity_type` = 'PHONE' AND i.`app_id` = '' AND i.`identifier` = w.`phone` COLLATE utf8mb4_general_ci
WHERE i.`id` IS NULL;

INSERT INTO `user_password` (`user_id`, `password_hash`)
SELECT 1000 + w.`id`, w.`password_hash`
FROM `web_user` w
LEFT JOIN `user_identity` i
  ON i.`identity_type` = 'PHONE' AND i.`app_id` = '' AND i.`identifier` = w.`phone` COLLATE utf8mb4_general_ci
WHERE i.`id` IS NULL
ON DUPLICATE KEY UPDATE `password_hash` = VALUES(`password_hash`);

-- ---------------------------------------------------------------------
-- 5. creator_profile / creator_item.creator_id：web_user.id → app_user.id
--    通过手机号找回目标账号（两类 web_user 均已具备 PHONE 凭据）
-- ---------------------------------------------------------------------
UPDATE `creator_profile` cp
JOIN `web_user` w ON cp.`creator_id` = w.`id`
JOIN `user_identity` i ON i.`identity_type` = 'PHONE' AND i.`app_id` = '' AND i.`identifier` = w.`phone` COLLATE utf8mb4_general_ci
SET cp.`creator_id` = i.`user_id`;

UPDATE `creator_item` ci
JOIN `web_user` w ON ci.`creator_id` = w.`id`
JOIN `user_identity` i ON i.`identity_type` = 'PHONE' AND i.`app_id` = '' AND i.`identifier` = w.`phone` COLLATE utf8mb4_general_ci
SET ci.`creator_id` = i.`user_id`;

-- ---------------------------------------------------------------------
-- 6. 旧表改名保留（不 DROP，可回滚）
-- ---------------------------------------------------------------------
RENAME TABLE `wx_user`            TO `wx_user_bak_20261023`,
             `web_user`           TO `web_user_bak_20261023`,
             `web_user_archive`   TO `web_user_archive_bak_20261023`;

-- ---------------------------------------------------------------------
-- 7. 表注释同步（语义已从「wx 域」变为「统一账号」）
-- ---------------------------------------------------------------------
ALTER TABLE `user_identity`
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '所属用户ID（指向 app_user.id，两端统一编号）',
  MODIFY COLUMN `app_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '应用标识：openid 配小程序 appid；手机号填空串',
  COMMENT = '用户登录凭据表（一人 N 条凭据；手机号唯一性由 uk_type_app_identifier 承担）';

ALTER TABLE `user_password`
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '用户ID（1:1 指向 app_user.id，两端共用）',
  COMMENT = '用户密码表（与 user_identity 分离，手机号流转不影响密码归属）';

-- ---------------------------------------------------------------------
-- 8. 校验（人工看结果，不参与迁移逻辑）
-- ---------------------------------------------------------------------
SELECT '账号总数' AS item, COUNT(*) AS cnt FROM `app_user`
UNION ALL SELECT '有手机号账号数', COUNT(DISTINCT `user_id`) FROM `user_identity` WHERE `identity_type` = 'PHONE'
UNION ALL SELECT '有密码账号数', COUNT(*) FROM `user_password`
UNION ALL SELECT 'creator_profile 孤儿行', COUNT(*) FROM `creator_profile` cp
            LEFT JOIN `app_user` a ON a.`id` = cp.`creator_id` WHERE a.`id` IS NULL
UNION ALL SELECT 'creator_item 孤儿行', COUNT(*) FROM `creator_item` ci
            LEFT JOIN `app_user` a ON a.`id` = ci.`creator_id` WHERE a.`id` IS NULL;
