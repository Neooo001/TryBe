USE trybe;

-- ============================================================================
-- 初始化运营管理员（ADMIN）账号
-- 项目：TryBe（AI 虚拟试穿平台，本科毕业设计）
-- 日期：2026-09-21（2026-09-25 适配「统一账号体系 v2」：手机号已抽离到 user_identity）
--
-- 【为什么需要这个脚本】
--   创作者现在可以在网页自助注册，但注册产生的一律是 CREATOR（创作者）。
--   系统里没有任何流程能把账号提升为 ADMIN —— 这是刻意为之：
--   运营权限不能被自助获取。所以 ADMIN 只能由你手工指定。
--
-- 【v2 变更点（重要）】
--   统一账号体系 v2 后，user 表不再保留 phone / openid / password 三列，
--   手机号改存 user_identity（identity_type='PHONE'）。因此本脚本改为
--   通过 user_identity 关联取手机号。执行本脚本前请确保已跑过
--   sql/userService/统一账号体系v2改造.sql（至少 Part 1~Part 5）。
--
-- 【用法】
--   1. 先在创作者后台用这个手机号注册一个账号（会得到一个 CREATOR 账号）
--   2. 把下面 @admin_phone 改成那个手机号
--   3. 执行本脚本
--   4. 回到网页重新登录（前端 role 缓存在 localStorage，不重登仍是 CREATOR）
--
-- 【幂等】可重复执行；已为 ADMIN 时 UPDATE 影响行数为 0，不会误改。
-- 【降权回滚】
--   UPDATE `user` u
--   JOIN `user_identity` i ON i.user_id = u.id AND i.identity_type = 'PHONE'
--   SET u.role = 'CREATOR' WHERE i.identifier = '你的手机号';
-- ============================================================================

SET @admin_phone = '13800138000';   -- ← 改成你要设为运营的手机号

-- ==================== 执行前：确认账号存在 ====================
-- 期望查到 1 行；若查不到，说明还没注册，请先在网页注册再回来跑
SELECT u.id, u.nickname, i.identifier AS phone, u.role, u.status
FROM `user` u
JOIN `user_identity` i ON i.user_id = u.id AND i.identity_type = 'PHONE'
WHERE i.identifier = @admin_phone;

-- ==================== 提升为运营 ====================
UPDATE `user` u
JOIN `user_identity` i ON i.user_id = u.id AND i.identity_type = 'PHONE'
SET u.role = 'ADMIN'
WHERE i.identifier = @admin_phone
  AND u.role <> 'ADMIN';

-- ==================== 执行后校验 ====================
-- 期望 role = 'ADMIN'
SELECT u.id, u.nickname, i.identifier AS phone, u.role, u.status
FROM `user` u
JOIN `user_identity` i ON i.user_id = u.id AND i.identity_type = 'PHONE'
WHERE i.identifier = @admin_phone;

-- 顺便看看全平台运营账号列表（手机号来自 user_identity）
SELECT u.id, u.nickname, i.identifier AS phone, u.role
FROM `user` u
LEFT JOIN `user_identity` i ON i.user_id = u.id AND i.identity_type = 'PHONE'
WHERE u.role = 'ADMIN';
