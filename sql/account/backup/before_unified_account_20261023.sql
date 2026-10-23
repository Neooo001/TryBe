-- MySQL dump 10.13  Distrib 8.0.44, for Win64 (x86_64)
--
-- Host: 121.40.26.23    Database: trybe
-- ------------------------------------------------------
-- Server version	8.0.27

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `wx_user`
--

DROP TABLE IF EXISTS `wx_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wx_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID（wx域主键；与 web_user.id 无任何关联，两域ID各自独立编号）',
  `nickname` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '小Try' COMMENT '用户昵称',
  `avatar` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '头像OSS存储地址',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '使用状态（0冻结 1正常）',
  `unionid` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '微信开放平台 unionid（预留，不启用）',
  `phone_state` tinyint NOT NULL DEFAULT '0' COMMENT '手机号归属状态：0从未绑定 1已绑定 2曾绑定被释放(待补绑)',
  `bio` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '个人介绍',
  `gender` tinyint DEFAULT NULL COMMENT '性别（0未知 1男 2女）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_unionid` (`unionid`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='小程序用户表（wx域；与创作者后台 web_user 完全独立、零交集，微信openid为身份标识，手机号仅为可选便利凭据）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wx_user`
--

LOCK TABLES `wx_user` WRITE;
/*!40000 ALTER TABLE `wx_user` DISABLE KEYS */;
INSERT INTO `wx_user` VALUES (1,'任世明','avatar/1/original.png',1,NULL,1,'你好啊，我是小明',1,'2026-09-25 18:01:17','2026-10-21 23:26:10');
/*!40000 ALTER TABLE `wx_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_identity`
--

DROP TABLE IF EXISTS `user_identity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_identity` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '凭据ID',
  `user_id` bigint NOT NULL COMMENT '所属用户ID（指向 wx_user.id，小程序域）',
  `identity_type` varchar(24) COLLATE utf8mb4_general_ci NOT NULL COMMENT '凭据类型：PHONE / WECHAT_OPENID',
  `app_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '应用标识：openid 必须配小程序 appid；手机号填空串',
  `identifier` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '凭据值：手机号 / openid',
  `verified` tinyint NOT NULL DEFAULT '0' COMMENT '是否已验证：0否 1是（手机号需过短信验证）',
  `verified_time` datetime DEFAULT NULL COMMENT '验证通过时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_app_identifier` (`identity_type`,`app_id`,`identifier`),
  KEY `idx_user` (`user_id`),
  KEY `idx_identifier` (`identifier`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户登录凭据表（一个用户 N 条凭据；手机号唯一性由 uk_type_app_identifier 承担）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_identity`
--

LOCK TABLES `user_identity` WRITE;
/*!40000 ALTER TABLE `user_identity` DISABLE KEYS */;
INSERT INTO `user_identity` VALUES (1,1,'WECHAT_OPENID','wxef63cdb43547af38','otndj10sZYC-ug_I1vGH8hrlp-28',1,'2026-09-25 18:01:17','2026-09-25 18:01:17','2026-09-25 18:01:17'),(2,1,'PHONE','','13837023740',1,'2026-10-20 18:16:40','2026-10-20 18:16:40','2026-10-20 18:16:40');
/*!40000 ALTER TABLE `user_identity` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_password`
--

DROP TABLE IF EXISTS `user_password`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_password` (
  `user_id` bigint NOT NULL COMMENT '用户ID（1:1 指向 wx_user.id，小程序域）',
  `password_hash` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '密码 BCrypt 哈希',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户密码表（与 user_identity 分离，手机号流转不影响密码归属）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_password`
--

LOCK TABLES `user_password` WRITE;
/*!40000 ALTER TABLE `user_password` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_password` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `web_user`
--

DROP TABLE IF EXISTS `web_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `web_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID（web域主键；与 wx_user.id 无任何关联，两域ID各自独立编号）',
  `phone` varchar(20) NOT NULL COMMENT '登录手机号（web域的账号标识，唯一键 uk_phone）',
  `password_hash` varchar(100) NOT NULL COMMENT '登录密码 BCrypt 哈希（明文不落库）',
  `nickname` varchar(64) NOT NULL COMMENT '用户昵称（注册未填时默认「小Try」）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '账号状态：0-冻结, 1-正常',
  `last_login_time` datetime DEFAULT NULL COMMENT '最近登录时间（沉睡归档判定依据：≥180天未登录，该手机号可被新注册者接手，原账号整行归档）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Web域用户表（创作者后台账号；与小程序 wx_user 完全独立、零交集）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `web_user`
--

LOCK TABLES `web_user` WRITE;
/*!40000 ALTER TABLE `web_user` DISABLE KEYS */;
INSERT INTO `web_user` VALUES (1,'13837023740','$2a$10$/XNm25FmLWKZaAsXmAc.Ae0G8Y4.IdN/E28nR7.8v51WMLwFMpPGS','renXiaoMing',1,'2026-10-21 23:24:15','2026-08-19 16:53:45','2026-10-21 23:24:15');
/*!40000 ALTER TABLE `web_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `web_user_archive`
--

DROP TABLE IF EXISTS `web_user_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `web_user_archive` (
  `id` bigint NOT NULL COMMENT '原 web_user 表主键（沿用原ID，便于回溯与人工还原）',
  `phone` varchar(20) NOT NULL COMMENT '被释放的手机号（新注册者将占用此号）',
  `password_hash` varchar(100) NOT NULL COMMENT '归档时的密码哈希（仅留档，不可用于登录）',
  `nickname` varchar(64) NOT NULL COMMENT '归档时的昵称',
  `status` tinyint NOT NULL COMMENT '归档时的账号状态：0-冻结, 1-正常',
  `last_login_time` datetime DEFAULT NULL COMMENT '归档时的最近登录时间',
  `create_time` datetime NOT NULL COMMENT '原账号创建时间',
  `archive_reason` varchar(32) NOT NULL DEFAULT 'SLEEP_RELEASE' COMMENT '归档原因：SLEEP_RELEASE-沉睡释放（≥180天未登录）',
  `archived_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
  PRIMARY KEY (`id`),
  KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Web域沉睡账号归档表（手机号被新注册者接手时，原账号整行从 web_user 搬迁至此，可回溯、可人工还原）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `web_user_archive`
--

LOCK TABLES `web_user_archive` WRITE;
/*!40000 ALTER TABLE `web_user_archive` DISABLE KEYS */;
/*!40000 ALTER TABLE `web_user_archive` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `creator_profile`
--

DROP TABLE IF EXISTS `creator_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `creator_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `creator_id` bigint NOT NULL COMMENT '创作者用户ID（web_user.id，web域；与 wx_user 零关联）',
  `nickname` varchar(32) NOT NULL COMMENT '创作者昵称（公开主页展示名，与 web_user.nickname 账号昵称相互独立）',
  `avatar_key` varchar(255) DEFAULT NULL COMMENT '头像 OSS objectKey（creator/{userId}/profile/avatar.{ext}；NULL=默认头像）',
  `cover_key` varchar(255) DEFAULT NULL COMMENT '主页封面图 OSS objectKey（creator/{userId}/profile/cover.{ext}；NULL=默认封面）',
  `bio` varchar(512) DEFAULT NULL COMMENT '个人简介',
  `field_codes` varchar(255) DEFAULT NULL COMMENT '创作方向编码，逗号分隔 CreatorField：HAIR_COLOR,OUTFIT,...（服务端限最多5个）',
  `homepage_status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '主页展示状态：ENABLED启用/DISABLED停用（仅管理端可改，创作者接口只读）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_creator` (`creator_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='创作者个人主页档案表（与 web_user 1:1，懒创建）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `creator_profile`
--

LOCK TABLES `creator_profile` WRITE;
/*!40000 ALTER TABLE `creator_profile` DISABLE KEYS */;
INSERT INTO `creator_profile` VALUES (1,1,'renXiaoMing','creator/1/profile/avatar.png','creator/1/profile/cover.png','身高165 / 微胖梨形','OUTFIT','ENABLED','2026-08-19 21:20:55','2026-08-19 21:20:55');
/*!40000 ALTER TABLE `creator_profile` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `creator_item`
--

DROP TABLE IF EXISTS `creator_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `creator_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `item_no` varchar(32) NOT NULL COMMENT '物品业务编号，如 CI20250101000001（全局唯一）',
  `creator_id` bigint NOT NULL COMMENT '创作者用户ID（web域，指向 web_user.id）',
  `category_code` varchar(32) DEFAULT NULL COMMENT '一级分类：TOP/BOTTOM/SET/SHOES/BAG/ACCESSORY/DECOR/OTHER',
  `title` varchar(256) DEFAULT NULL COMMENT '物品标题',
  `description` varchar(2048) DEFAULT NULL COMMENT '物品描述',
  `link_url` varchar(512) DEFAULT NULL COMMENT '【创作者】作品外链（购买链接/同款链接）',
  `price` decimal(10,2) DEFAULT NULL COMMENT '价格（元）',
  `status` varchar(16) DEFAULT NULL COMMENT '状态：DRAFT-草稿, ON_SHELF-已上架, OFF_SHELF-已下架',
  `tryon_mode` varchar(16) DEFAULT NULL COMMENT '试穿模式：FLAT-平铺, MODEL-模特, BOTH-两者',
  `ai_color_hex` varchar(16) DEFAULT NULL COMMENT '【AI】主色 HEX，如 #RRGGBB',
  `ai_color_name` varchar(32) DEFAULT NULL COMMENT '【AI】主色中文名，如 浅灰蓝',
  `ai_seasons` varchar(64) DEFAULT NULL COMMENT '【AI】判定季节，JSON数组（与创作者的 seasons 各存一份，不合并）',
  `material` varchar(128) DEFAULT NULL COMMENT '【创作者】材质成分，如 棉95%/氨纶5%',
  `pattern_tags` varchar(512) DEFAULT NULL COMMENT '图案/风格标签（JSON数组字符串）',
  `seasons` varchar(64) DEFAULT NULL COMMENT '【创作者】适用季节，JSON数组：["SPRING","SUMMER","AUTUMN","WINTER"]',
  `styles` varchar(512) DEFAULT NULL COMMENT '【创作者】衣服款式，JSON数组，如 ["直筒裤","休闲","高腰"]',
  `ai_styles` varchar(512) DEFAULT NULL COMMENT '【AI】风格/款式标签，JSON数组（与创作者的 styles 各存一份，不合并）',
  `ai_material` varchar(128) DEFAULT NULL COMMENT '【AI】材质判定（与创作者的 material 各存一份，不合并）',
  `ai_selling_points` varchar(255) DEFAULT NULL COMMENT '【AI】推广卖点文案（限40字）',
  `ai_suggested_price` decimal(10,2) DEFAULT NULL COMMENT '【AI】建议零售价（元），仅供参考，与实际售价 price 无关',
  `ai_parsed` tinyint NOT NULL DEFAULT '0' COMMENT '商品信息是否来自 AI 解析：0否/1是',
  `ai_model` varchar(32) DEFAULT NULL COMMENT '完成 AI 解析时使用的模型名，如 qwen-vl-max',
  `ai_parsed_at` datetime DEFAULT NULL COMMENT 'AI 解析时间',
  `zone` varchar(16) DEFAULT NULL COMMENT '作品分区：上衣/下衣/连衣裙/外套/鞋靴/包袋配饰/发色/美瞳',
  `topics` varchar(255) DEFAULT NULL COMMENT '作品话题，JSON数组（内容栏目，用于聚合与推荐）',
  `image_key` varchar(512) DEFAULT NULL COMMENT '主图 OSS objectKey（白底/平铺展示图）',
  `cutout_png_key` varchar(512) DEFAULT NULL COMMENT '透明底 PNG OSS objectKey（试穿素材）',
  `reference_keys` text COMMENT '参考图 objectKey 列表（JSON数组）',
  `real_width_mm` int DEFAULT NULL COMMENT '实物宽度（毫米）',
  `real_height_mm` int DEFAULT NULL COMMENT '实物高度（毫米）',
  `px_per_mm` decimal(10,4) DEFAULT NULL COMMENT '尺寸标定结果：每毫米像素数（前端 Canvas 标定后回传）',
  `anchor_json` text COMMENT '锚点标注JSON，如 {"neck":[x,y],"shoulderL":[x,y]}',
  `ai_product_name` varchar(128) DEFAULT NULL COMMENT '【AI】商品名，推广用，与作品标题 title 解耦',
  `ai_category_code` varchar(32) DEFAULT NULL COMMENT '【AI】判定的一级分类码 TOP/BOTTOM/SET/SHOES/BAG/ACCESSORY/DECOR/OTHER（创作者选的分类另存 category_code）',
  `demo_model_id` bigint DEFAULT NULL COMMENT '演示模特ID（预留字段，v1 不启用，属 tryon-service）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item_no` (`item_no`),
  KEY `idx_creator_id` (`creator_id`),
  KEY `idx_creator_status` (`creator_id`,`status`),
  KEY `idx_ai_parsed` (`ai_parsed`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='创作者物品表（创作者商品池，与 wardrobe_item / product 无外键关联）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `creator_item`
--

LOCK TABLES `creator_item` WRITE;
/*!40000 ALTER TABLE `creator_item` DISABLE KEYS */;
INSERT INTO `creator_item` VALUES (8,'CI20261014000005',1,'TOP','上衣','最帅的上衣',NULL,NULL,'ON_SHELF','MODEL','#707070','灰色','[\"SPRING\",\"AUTUMN\"]','棉100%','[\"显瘦穿搭\",\"通勤\",\"一衣多穿\"]','[\"AUTUMN\",\"SUMMER\"]','[\"针织衫\",\"西装外套\"]','[\"轻量\",\"透气\",\"速干\",\"运动\",\"休闲\",\"纯色\",\"修身\",\"运动休闲\"]','速干聚酯纤维','立领拉链设计，轻薄透气，适合春秋运动与通勤穿搭',259.00,1,'qwen3-vl-plus','2026-10-14 21:38:08','上衣','[\"秋冬上新\",\"发色试色\",\"一衣多穿\"]','creator/1/items/8/model_6961a64f80b6413e82ee099c32b7d5fd.png',NULL,NULL,NULL,NULL,NULL,NULL,'灰色立领拉链运动外套','TOP',NULL,'2026-08-20 04:59:40','2026-08-20 04:59:48',0),(9,'CI20261020000001',1,'TOP','2','2',NULL,NULL,'ON_SHELF','MODEL','#1A2332','深蓝','[\"AUTUMN\",\"WINTER\"]',NULL,'[\"显瘦穿搭\"]','[\"SUMMER\"]','[\"针织衫\"]','[\"连帽\",\"防风\",\"轻量\",\"户外\",\"机能\",\"纯色\",\"常规\",\"户外机能\"]','防风防水面料','连帽防风设计，轻量耐磨，适合秋冬户外与城市通勤',899.00,1,'qwen3-vl-plus','2026-10-20 15:33:09','上衣','[\"秋冬上新\"]','creator/1/items/9/model_afca9a8ec8d44720a8e3d52c0e32074d.png',NULL,NULL,NULL,NULL,NULL,NULL,'深蓝色连帽防风夹克','TOP',NULL,'2026-08-20 10:40:38','2026-08-20 10:40:47',0),(10,'CI20261020000002',1,'BOTTOM','白色裤子','棉裤',NULL,NULL,'ON_SHELF','MODEL','#E0E0E0','浅灰','[\"SPRING\",\"AUTUMN\",\"WINTER\"]','棉98%','[\"显瘦穿搭\"]','[\"SUMMER\",\"AUTUMN\"]','[\"直筒裤\"]','[\"宽松\",\"高腰\",\"垂感好\",\"简约\",\"休闲\",\"纯色\",\"宽松直筒\",\"简约休闲\"]','棉质卫衣布','高腰宽松版型，柔软垂顺，日常通勤皆宜',159.00,1,'qwen3-vl-plus','2026-10-20 17:32:18','下衣','[\"秋冬上新\"]','creator/1/items/10/model_9e18d4a3113049818e05b284c3209ea5.png',NULL,NULL,NULL,NULL,NULL,NULL,'浅灰色宽松休闲卫裤','BOTTOM',NULL,'2026-08-20 11:06:53','2026-08-20 11:07:03',0),(11,'CI20261021000001',1,'TOP','连衣裙','立案一瘸',NULL,NULL,'OFF_SHELF','MODEL','#F5F0E6','米白色','[\"SPRING\",\"SUMMER\",\"AUTUMN\"]','棉','[\"显瘦穿搭\"]','[\"SPRING\",\"SUMMER\"]','[\"半身裙\"]','[\"无袖\",\"抽褶\",\"包臀\",\"弹力\",\"修身\",\"简约\",\"纯色\",\"修身包臀\"]','弹力针织','一字领无袖设计，抽褶修身显瘦，弹力面料贴合不紧绷',122.00,1,'qwen3-vl-plus','2026-10-21 23:23:39','上衣','[\"每日穿搭\",\"显瘦穿搭\"]','creator/1/items/11/model_efd743e7a28b4f018290a8c45398ef64.jpg',NULL,NULL,NULL,NULL,NULL,NULL,'米白色一字领无袖修身弹力抽褶包臀连衣裙','SET',NULL,'2026-10-21 23:25:08','2026-10-23 00:20:27',0);
/*!40000 ALTER TABLE `creator_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_phone_history`
--

DROP TABLE IF EXISTS `user_phone_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_phone_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '曾绑定该号的账号（即被释放的 A）',
  `phone` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '曾绑定的手机号',
  `bound_time` datetime DEFAULT NULL COMMENT '上次绑定该号的时间',
  `released_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '释放时间',
  `release_reason` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PHONE_TRANSFERRED' COMMENT '释放原因：PHONE_TRANSFERRED 被其他账号抢绑',
  PRIMARY KEY (`id`),
  KEY `idx_phone` (`phone`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='曾绑定但已释放的手机号（恢复路径 + 审计）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_phone_history`
--

LOCK TABLES `user_phone_history` WRITE;
/*!40000 ALTER TABLE `user_phone_history` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_phone_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `phone_transfer_log`
--

DROP TABLE IF EXISTS `phone_transfer_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `phone_transfer_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `transfer_no` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '幂等键（服务端按 B+phone+时间窗生成）',
  `phone` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '被转移的手机号',
  `from_user_id` bigint DEFAULT NULL COMMENT '被释放方 A（无占用者时为 NULL）',
  `to_user_id` bigint NOT NULL COMMENT '获得方 B',
  `scene` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发场景：BIND_PHONE',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '转移时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transfer_no` (`transfer_no`),
  KEY `idx_phone` (`phone`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='手机号归属转移日志（uk_transfer_no 保证同一请求不重复执行）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `phone_transfer_log`
--

LOCK TABLES `phone_transfer_log` WRITE;
/*!40000 ALTER TABLE `phone_transfer_log` DISABLE KEYS */;
INSERT INTO `phone_transfer_log` VALUES (1,'5f87e7809d0d495baaf437f83a51346f','13837023740',NULL,1,'BIND_PHONE','2026-10-20 18:16:40');
/*!40000 ALTER TABLE `phone_transfer_log` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-23 18:40:12
