-- ============================================================
-- TryBe 双账号域拆分 · 数据库变更脚本
-- 场景：小程序（wx 域 wx_user）与 Web 后台（web 域 web_user）完全两套账号
-- ⚠️ 不可逆操作：全库业务数据清空。执行前必须先备份：
--    mysqldump -uroot -p --single-transaction --routines --triggers --databases trybe > D:\backup\trybe_备份.sql
-- ============================================================

-- 1. user 表改名 wx_user（结构原样保留，MyBatis-Plus 侧由 @TableName("wx_user") 对应）
RENAME TABLE `user` TO wx_user;

-- 2. 全库清空（账号表 + 业务表）
TRUNCATE TABLE wx_user;
TRUNCATE TABLE user_identity;
TRUNCATE TABLE user_password;
TRUNCATE TABLE user_phone_history;
TRUNCATE TABLE phone_transfer_log;
TRUNCATE TABLE wardrobe_item;
TRUNCATE TABLE wardrobe_category;
TRUNCATE TABLE wardrobe_import_task;
TRUNCATE TABLE body_profile;
TRUNCATE TABLE body_profile_material;
TRUNCATE TABLE material;
TRUNCATE TABLE material_check_log;
-- creator-service 的表若已建表则一并清空（当前库尚未建）
-- TRUNCATE TABLE creator_item;
-- TRUNCATE TABLE creator_item_model_image;

-- 3. wx_user 删除角色列（Web 单一角色化，wx 域也不再有 role），并同步表/主键注释
ALTER TABLE wx_user DROP COLUMN role;
ALTER TABLE wx_user
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID（wx域主键；与 web_user.id 无任何关联，两域ID各自独立编号）',
  COMMENT = '小程序用户表（wx域；与创作者后台 web_user 完全独立、零交集，微信openid为身份标识，手机号仅为可选便利凭据）';

-- 4. web 域用户表（手机号即账号标识，密码内嵌，单表）
CREATE TABLE web_user (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID（web域主键；与 wx_user.id 无任何关联，两域ID各自独立编号）',
  phone           VARCHAR(20)  NOT NULL COMMENT '登录手机号（web域的账号标识，唯一键 uk_phone）',
  password_hash   VARCHAR(100) NOT NULL COMMENT '登录密码 BCrypt 哈希（明文不落库）',
  nickname        VARCHAR(64)  NOT NULL COMMENT '用户昵称（注册未填时默认「小Try」）',
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '账号状态：0-冻结, 1-正常',
  last_login_time DATETIME     NULL COMMENT '最近登录时间（沉睡归档判定依据：≥180天未登录，该手机号可被新注册者接手，原账号整行归档）',
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Web域用户表（创作者后台账号；与小程序 wx_user 完全独立、零交集）';

-- 5. web 域沉睡归档表（整行搬迁：主表 phone 保持干净、UNIQUE 兜底释放语义、可回溯可还原）
CREATE TABLE web_user_archive (
  id              BIGINT       NOT NULL COMMENT '原 web_user 表主键（沿用原ID，便于回溯与人工还原）',
  phone           VARCHAR(20)  NOT NULL COMMENT '被释放的手机号（新注册者将占用此号）',
  password_hash   VARCHAR(100) NOT NULL COMMENT '归档时的密码哈希（仅留档，不可用于登录）',
  nickname        VARCHAR(64)  NOT NULL COMMENT '归档时的昵称',
  status          TINYINT      NOT NULL COMMENT '归档时的账号状态：0-冻结, 1-正常',
  last_login_time DATETIME     NULL COMMENT '归档时的最近登录时间',
  create_time     DATETIME     NOT NULL COMMENT '原账号创建时间',
  archive_reason  VARCHAR(32)  NOT NULL DEFAULT 'SLEEP_RELEASE' COMMENT '归档原因：SLEEP_RELEASE-沉睡释放（≥180天未登录）',
  archived_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
  PRIMARY KEY (id),
  KEY idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Web域沉睡账号归档表（手机号被新注册者接手时，原账号整行从 web_user 搬迁至此，可回溯、可人工还原）';

-- 6. 修正旧表中「指向 user.id」的过时注释（表已改名 wx_user）
ALTER TABLE user_identity
  MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '所属用户ID（指向 wx_user.id，小程序域）';
ALTER TABLE user_password
  MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '用户ID（1:1 指向 wx_user.id，小程序域）';

-- 7. 验证
SHOW TABLES;
