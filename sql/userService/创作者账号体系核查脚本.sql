-- =============================================================
-- TryBe 创作者后台 —— 账号体系核查脚本（只交付，不代执行）
-- 目标库：trybe（数据库在虚拟机 192.168.100.128）
-- 说明：本轮改造不新增表。图形验证码与短信限流全在 Redis，
--       升级为创作者只改 user.role。本脚本仅用于上线前核查。
--       可直接用 Navicat / DataGrip 执行，未使用 DELIMITER。
-- =============================================================

USE trybe;

-- 1) 检查是否存在重复手机号
--    返回 0 行才具备给 user.phone 加唯一索引的前提。
SELECT phone, COUNT(*) AS cnt
FROM `user`
GROUP BY phone
HAVING cnt > 1;

-- 2)【可选加固】仅当第 1 步返回 0 行时再放开注释执行。
--    作用：从根上杜绝并发重复注册；注册逻辑可改为捕获 DuplicateKeyException。
--    若线上已有脏数据，这条 DDL 会失败，需先清理重复行。
-- ALTER TABLE `user` ADD UNIQUE KEY `uk_phone` (`phone`);

-- 3) 查看角色分布（确认升级接口效果）
SELECT role, COUNT(*) AS cnt
FROM `user`
GROUP BY role;

-- 4) 查看无密码的账号（这类账号在小程序侧是微信登录建的，
--    若要用手机号登录创作者后台，需先走「绑定手机号 + 设置密码」）
SELECT COUNT(*) AS no_password_count
FROM `user`
WHERE password IS NULL OR password = '';

-- 5) 查看已绑定手机号但未设置密码的账号明细（便于自查）
SELECT id, nickname, phone, role, status, create_time
FROM `user`
WHERE phone IS NOT NULL
  AND (password IS NULL OR password = '')
ORDER BY id DESC
LIMIT 50;
