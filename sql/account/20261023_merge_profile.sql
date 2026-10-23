-- =====================================================================
-- TryBe 资料统一：昵称 / 头像 / 简介 收敛到 app_user（2026-10-23）
--
--   背景：v3 统一账号后，同一个人仍有「两套资料」——
--     小程序「我的」读 app_user.nickname / avatar / bio
--     圈子作品卡片 + Web 个人中心读 creator_profile.nickname / avatar_key / bio
--   结果：在 Web 完善资料，小程序看到的还是旧的；反之亦然。
--
--   本脚本把 creator_profile 的这三项一次性搬到 app_user，此后：
--     app_user          = 唯一资料源（两端 + 圈子卡片都读它）
--     creator_profile   = 只保留 cover_key / field_codes / homepage_status
--                         三个列冻结（保留数据，代码不再读写），观察期后再 DROP
--
--   ⚠️ 只执行一次。creator_profile 冻结后若再次执行，会用历史值覆盖用户新改的昵称。
-- =====================================================================

SET NAMES utf8mb4;

-- 1. 迁移：creator_profile 有值则覆盖 app_user（本次口径：Web 设置过的值优先）
UPDATE `app_user` u
JOIN `creator_profile` p ON p.`creator_id` = u.`id`
SET u.`nickname`    = COALESCE(NULLIF(TRIM(p.`nickname`), ''), u.`nickname`),
    u.`avatar`      = COALESCE(NULLIF(TRIM(p.`avatar_key`), ''), u.`avatar`),
    u.`bio`         = COALESCE(NULLIF(TRIM(p.`bio`), ''), u.`bio`),
    u.`update_time` = NOW();

-- 2. 冻结三列：保留历史值不删，注释标明不再是事实源
ALTER TABLE `creator_profile`
  MODIFY COLUMN `nickname`   varchar(32)  NOT NULL COMMENT '[已冻结 2026-10-23] 历史字段，昵称统一在 app_user.nickname，本列不再读写',
  MODIFY COLUMN `avatar_key` varchar(255) DEFAULT NULL COMMENT '[已冻结 2026-10-23] 历史字段，头像统一在 app_user.avatar，本列不再读写',
  MODIFY COLUMN `bio`        varchar(512) DEFAULT NULL COMMENT '[已冻结 2026-10-23] 历史字段，简介统一在 app_user.bio，本列不再读写';

-- 3. 校验：两端资料应完全一致
SELECT u.`id`, u.`nickname` AS app_nickname, p.`nickname` AS creator_nickname,
       u.`avatar` AS app_avatar, p.`avatar_key` AS creator_avatar,
       u.`bio` AS app_bio, p.`bio` AS creator_bio
FROM `app_user` u
LEFT JOIN `creator_profile` p ON p.`creator_id` = u.`id`;
