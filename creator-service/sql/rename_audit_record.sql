USE trybe;

-- ============================================================
-- TryBe · 审核流水表停用（改名备份，保留回滚能力，不 DROP）
--
-- 【部署顺序】备份 -> migrate_creator_item_status.sql -> 本脚本 -> 部署新代码
--
-- 【执行方式】本脚本在 Navicat / DataGrip 等图形客户端中直接全选执行即可，
--            不含 DELIMITER 等 mysql CLI 专有指令。
--
-- 【执行次数】本脚本只需执行一次。
--            若执行时报错 "Table 'audit_record' doesn't exist"，
--            说明已经改过名了（或该表本就不存在），可安全跳过，无需处理。
--
-- 【前置检查】（可选，单独选中执行）
--   SHOW TABLES LIKE 'audit_record';
--   SHOW TABLES LIKE 'audit_record_bak_20250921';
--
-- 【说明】新代码已删除 AuditRecord PO / Mapper / Service / Controller，
--        改名后无任何引用；表内历史流水（SUBMIT/APPROVE/REJECT/上下架）完整保留。
--
-- 【回滚】RENAME TABLE `audit_record_bak_20250921` TO `audit_record`;
-- ============================================================

RENAME TABLE `audit_record` TO `audit_record_bak_20250921`;

-- ---------- 自查：应只看到 audit_record_bak_20250921，不再看到 audit_record ----------
SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('audit_record', 'audit_record_bak_20250921');
