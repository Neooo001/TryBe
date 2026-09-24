USE trybe;

-- ============================================================
-- TryBe · creator_item 状态机简化迁移（发布即上架）
--
-- 【部署顺序（务必遵守）】
--   1) 备份 creator_item 表（mysqldump / 复制表，二选一）
--   2) 执行本脚本（状态迁移）
--   3) 执行 rename_audit_record.sql（审核流水表改名备份）
--   4) 部署新代码
--
--   原因：若先部署新代码再迁移，存量 PENDING/APPROVED/REJECTED 会被
--   CreatorItemStatus.of() 解析为 null，详情页状态显示「-」、
--   编辑/删除直接抛 STATUS_ERROR「当前状态不可编辑」。
--   故 SQL 先行，代码后上。
--
-- 【执行方式】Navicat / DataGrip 等图形客户端全选执行即可，不含 CLI 专有指令。
-- 【映射】PENDING -> ON_SHELF、APPROVED -> ON_SHELF、REJECTED -> DRAFT
-- 【幂等】可重复执行，重复执行时各条影响行数递减为 0
-- 【口径】三条 UPDATE 均加 deleted = 0，与业务表逻辑删除约定保持一致
-- 【说明】reject_reason 物理列保留（回滚无需改表结构），仅把值刷成 NULL；
--        Java 侧已删除该字段，不存在新写入。
-- ============================================================

-- ---------- 0) 预检：查看变更前各状态的行数（仅 SELECT，不做修改） ----------
SELECT `status`, COUNT(*) AS `cnt`
FROM `creator_item`
WHERE `deleted` = 0
GROUP BY `status`;

SELECT COUNT(*) AS `reject_reason_not_null`
FROM `creator_item`
WHERE `reject_reason` IS NOT NULL AND `deleted` = 0;

-- ---------- 1) 待审 / 已通过 -> 已上架 ----------
UPDATE `creator_item`
SET `status` = 'ON_SHELF', `update_time` = NOW()
WHERE `status` IN ('PENDING', 'APPROVED')
  AND `deleted` = 0;

-- ---------- 2) 已驳回 -> 草稿（创作者可重新编辑后发布） ----------
UPDATE `creator_item`
SET `status` = 'DRAFT', `update_time` = NOW()
WHERE `status` = 'REJECTED'
  AND `deleted` = 0;

-- ---------- 3) 清空驳回原因（物理列保留，值置 NULL） ----------
UPDATE `creator_item`
SET `reject_reason` = NULL
WHERE `reject_reason` IS NOT NULL
  AND `deleted` = 0;

-- ---------- 4) 结果校验：status 应只出现 DRAFT / ON_SHELF / OFF_SHELF ----------
SELECT `status`, COUNT(*) FROM `creator_item` WHERE `deleted` = 0 GROUP BY `status`;

-- ---------- 5) 结果校验：应为 0 ----------
SELECT COUNT(*) FROM `creator_item` WHERE `reject_reason` IS NOT NULL AND `deleted` = 0;

-- ---------- （可选）6) 同步列注释，使 DDL 与新状态机一致 ----------
-- ALTER TABLE `creator_item`
--   MODIFY COLUMN `status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
--   COMMENT 'DRAFT草稿/ON_SHELF已上架/OFF_SHELF已下架';

-- ============================================================
-- 【回滚说明】
--   ON_SHELF 无法区分原 PENDING 还是 APPROVED，反向 UPDATE 依赖第 1 步的表备份。
--   回滚语句示例（需按备份逐行比对）：
--     RENAME TABLE `creator_item_bak` TO `creator_item`;
-- ============================================================
