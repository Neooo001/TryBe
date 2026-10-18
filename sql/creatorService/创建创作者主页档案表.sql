USE trybe;

-- ============================================================
-- TryBe · 创作者个人中心 v1 DDL（MySQL 8，库 trybe）
--
-- 【背景】创作者个人中心 = 公开主页资料（可编辑）+ 账号资料（只读）。
--   - 公开主页资料存本表 creator_profile（与 web_user 1:1，懒创建）；
--   - 账号资料（手机号/账号昵称/创建时间/最近登录时间）不落本表，
--     由 creator-service 共享 trybe 库对 web_user 做只读投影获取，
--     避免账号字段双写漂移（与 CreatorApplication「共享库只读 user 表」约定一致）。
--
-- 【幂等】可重复执行：CREATE TABLE IF NOT EXISTS + 回填 INSERT 带 NOT EXISTS 守卫。
-- 【执行方式】Navicat / DataGrip 等图形客户端全选执行即可。
-- ============================================================

-- 创作者个人主页档案表（与 web_user 1:1；首次访问 GET /creator/profile 时懒创建默认行）
CREATE TABLE IF NOT EXISTS `creator_profile` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `creator_id`      BIGINT       NOT NULL COMMENT '创作者用户ID（web_user.id，web域；与 wx_user 零关联）',
    `nickname`        VARCHAR(32)  NOT NULL COMMENT '创作者昵称（公开主页展示名，与 web_user.nickname 账号昵称相互独立）',
    `avatar_key`      VARCHAR(255) NULL     COMMENT '头像 OSS objectKey（creator/{userId}/profile/avatar.{ext}；NULL=默认头像）',
    `cover_key`       VARCHAR(255) NULL     COMMENT '主页封面图 OSS objectKey（creator/{userId}/profile/cover.{ext}；NULL=默认封面）',
    `bio`             VARCHAR(512) NULL     COMMENT '个人简介',
    `field_codes`     VARCHAR(255) NULL     COMMENT '创作方向编码，逗号分隔 CreatorField：HAIR_COLOR,OUTFIT,...（服务端限最多5个）',
    `homepage_status` VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '主页展示状态：ENABLED启用/DISABLED停用（仅管理端可改，创作者接口只读）',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_creator` (`creator_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '创作者个人主页档案表（与 web_user 1:1，懒创建）';

-- ---------- 存量创作者回填：已有作品的 web 用户补一条默认档案行 ----------
-- 说明：接口层 GET /creator/profile 也会懒创建，本回填仅为让存量数据立即齐整、
--       列表类后台统计不至于漏人；默认昵称取 web_user.nickname（注册昵称）。
INSERT INTO `creator_profile` (`creator_id`, `nickname`)
SELECT w.`id`, w.`nickname`
FROM `web_user` w
WHERE EXISTS (SELECT 1 FROM `creator_item` ci WHERE ci.`creator_id` = w.`id` AND ci.`deleted` = 0)
  AND NOT EXISTS (SELECT 1 FROM `creator_profile` p WHERE p.`creator_id` = w.`id`);

-- ---------- 结果校验 ----------
-- 校验1：已有作品但仍缺档案行的创作者数 —— 应为 0（回填成功）
SELECT COUNT(*) AS `missing_profile`
FROM `creator_item` ci
WHERE ci.`deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `creator_profile` p WHERE p.`creator_id` = ci.`creator_id`);

-- 校验2：同一 creator_id 出现多条档案行 —— 应为 0（uk_creator 唯一键兜底，理论不可达）
SELECT `creator_id`, COUNT(*) AS `cnt`
FROM `creator_profile`
GROUP BY `creator_id`
HAVING COUNT(*) > 1;

-- 校验3：档案行总量与展示状态分布（人工核对数量是否符合预期）
SELECT `homepage_status`, COUNT(*) AS `cnt`
FROM `creator_profile`
GROUP BY `homepage_status`;

-- ============================================================
-- 【回滚说明】
--   DROP TABLE IF EXISTS `creator_profile`;
--   （头像/封面 OSS 对象位于 creator/{userId}/profile/ 前缀，可按前缀清理，不强制）
-- ============================================================

-- ============================================================
-- 【存储桶变更：头像一个 bucket —— 无需改 SQL，仅迁移 OSS 对象】
--
-- 头像 / 主页封面图已从默认桶 `trybe` 迁到独立桶 `trybe-avatar`
-- （配置项 aliyun.oss.bucket-name2，见 sql/creatorService/Nacos配置步骤.md 第 5 节）。
--
-- 关键点：**objectKey 不变** —— 仍是 creator/{userId}/profile/avatar.{ext} 与 cover.{ext}，
-- 因此本表 avatar_key / cover_key 存量数据<b>不需要任何 UPDATE</b>，
-- 只需把 OSS 对象按原路径从 `trybe` 复制到 `trybe-avatar` 后删除源对象。
--
-- 该桶 ACL 为「**公共读 / 私有写**」（控制台 → trybe-avatar → 权限管理 → 读写权限 → 公共读），
-- 配合 `aliyun.oss.bucket-name2-public-read: true` 后，接口下发的 avatarUrl / coverUrl
-- 是**永久 URL**（无 Expires/Signature，不过期、可缓存）；
-- 写入侧（P1 预签名 PUT URL）依旧需要签名，公共读只放开匿名读。
--
-- 例子（ossutil，复制 + 核对 + 删源）：
--   ossutil cp -r oss://trybe/creator/ oss://trybe-avatar/creator/ \
--     --include "*/profile/*" --update
--   ossutil ls oss://trybe-avatar/creator/ -r          -- 核对对象数量与路径
--   ossutil rm -r oss://trybe/creator/ --include "*/profile/*" -f
--
-- 迁移后可用下面的 SQL 列出「仍指向已迁移前缀」的档案行，逐条去新桶核对对象是否存在：
-- ============================================================

-- 校验4：已有头像/封面绑定的档案行（迁移后应能在 trybe-avatar 桶中找到同 objectKey 的对象）
SELECT `creator_id`, `avatar_key`, `cover_key`
FROM `creator_profile`
WHERE `avatar_key` IS NOT NULL
   OR `cover_key` IS NOT NULL;
