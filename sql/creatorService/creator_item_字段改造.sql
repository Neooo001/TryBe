-- ============================================================================
-- creator_item 字段改造：「创作者手填」与「AI 解析」分列，用 `ai_` 前缀区分
--
-- 需求：上传作品时创作者可手动填写/修改 —— 图片（增删）、标题、描述、链接、
--       季节、衣服款式、材质成分；其中「季节 / 衣服款式 / 材质成分」AI 也会解析，
--       但**不与创作者的值合并**，各写各的列，用前缀区分：
--           创作者手填 → seasons / styles / material
--           AI 自动解析 → ai_seasons / ai_styles / ai_material
--
-- 执行库：trybe  服务器：192.168.100.128:3306   MySQL 8.0.27  utf8mb4
--
-- ⚠️ 本脚本**尚未执行**，需经确认后再跑。执行前请先跑【第 0 步】备份。
-- ⚠️ 本脚本取代 sql/creatorService/商品信息推广字段.sql（已废弃，勿再执行）
-- ============================================================================


-- ============================================================================
-- 【变更范围总览】共 8 新增 / 3 改名 / 2 放宽 / 1 索引（可选）。不动任何行数据。
-- ----------------------------------------------------------------------------
-- 序号 | 变更             | 对象                  | 类型               | 说明
-- -----|------------------|-----------------------|--------------------|----------------------
--  1   | 新增             | link_url              | VARCHAR(512) NULL  | 创作者：作品外链
--  2   | 新增             | styles                | VARCHAR(512) NULL  | 创作者：衣服款式（JSON数组）
--  3   | 语义归位         | seasons               | VARCHAR(64)  不变  | 由「AI 产出」改为「创作者填写」，值不变
--  4   | 放宽             | material              | 64 → 128           | 创作者：材质成分，如 棉95%/氨纶5%
--  5   | 改名             | color_hex → ai_color_hex | VARCHAR(16) 不变 | AI 独占，按前缀归位
--  6   | 改名 + 放宽      | style_tags → ai_styles   | 255 → 512        | AI 独占，腾出 styles 给创作者
--  7   | 改名             | selling_points → ai_selling_points | VARCHAR(255) 不变 | AI 独占，按前缀归位
--  8   | 新增             | ai_product_name       | VARCHAR(128) NULL  | AI：商品名
--  9   | 新增             | ai_category_code      | VARCHAR(32)  NULL  | AI：判定分类（原先 AI 直接覆盖 category_code，属越界，必须分列）
-- 10   | 新增             | ai_color_name         | VARCHAR(32)  NULL  | AI：主色中文名
-- 11   | 新增             | ai_seasons            | VARCHAR(64)  NULL  | AI：判定季节
-- 12   | 新增             | ai_material           | VARCHAR(128) NULL  | AI：材质判定
-- 13   | 新增             | ai_suggested_price    | DECIMAL(10,2) NULL | AI：建议零售价
-- 14   | 新增索引（可选） | idx_ai_parsed         | (ai_parsed)        | 供将来「解析失败补偿」定时任务扫描
-- ----------------------------------------------------------------------------
-- 已存在、**不需要动**的列：id / item_no / creator_id / category_code / title /
--   description / price / status / tryon_mode / pattern_tags / seasons /
--   ai_parsed / ai_model / ai_parsed_at / zone / topics / image_key /
--   cutout_png_key / reference_keys / real_width_mm / real_height_mm /
--   px_per_mm / anchor_json / demo_model_id / create_time / update_time / deleted
--
-- 【图片增删不需要 DDL】落在 creator_item_model_image（已有 15 列，含 deleted /
--   update_time），接口已具备：
--       POST   /creator/items/{id}/model-images   绑定（新增）
--       GET    /creator/items/{id}/model-images   列出
--       DELETE /creator/model-images/{imageId}    逻辑删除
--   creator_item 侧只需 image_key 作为主图指针（已存在）。
-- ============================================================================


-- ============================================================================
-- 【执行前实测现状】（2026-10-13 读取 information_schema，作为对照基线）
-- ----------------------------------------------------------------------------
-- creator_item 共 31 列、2 行数据（id=1,2，均为 DRAFT，title=修身上衣）
-- 当前索引：PRIMARY(id)、UNIQUE uk_item_no(item_no)、
--           KEY idx_creator_id(creator_id)、KEY idx_creator_status(creator_id,status)
-- 当前 AUTO_INCREMENT = 3
--
-- 待改列的数据情况（决定改名是否安全）：
--   color_hex 非空 0 行 / material 非空 0 行 / seasons 非空 0 行 /
--   style_tags 非空 0 行 / selling_points 非空 0 行 / ai_parsed=1 共 0 行
--   ⇒ 上述列全是空的，**改名与语义翻转零数据风险**
--
-- 另：旧脚本 商品信息推广字段.sql 只被**部分执行**过 ——
--   seasons / style_tags / selling_points / ai_parsed / ai_model / ai_parsed_at /
--   zone / topics 已存在；product_name / color_name / suggested_price **未创建**。
--   因此本脚本用 ai_ 前缀新建后三者，不存在重名冲突。
-- ============================================================================

USE trybe;


-- ============================================================================
-- 第 0 步：备份（必做）
-- ============================================================================
CREATE TABLE IF NOT EXISTS `creator_item_bak_20261013`
    AS SELECT * FROM `creator_item`;
-- 校验备份行数应与源表一致（当前应为 2）
-- SELECT (SELECT COUNT(*) FROM creator_item) AS src,
--        (SELECT COUNT(*) FROM creator_item_bak_20261013) AS bak;


-- ============================================================================
-- 第 1 步：新增「创作者可填」列
-- ============================================================================
ALTER TABLE `creator_item`
    ADD COLUMN `link_url` VARCHAR(512) NULL
        COMMENT '【创作者】作品外链（购买链接/同款链接）' AFTER `description`,
    ADD COLUMN `styles` VARCHAR(512) NULL
        COMMENT '【创作者】衣服款式，JSON数组，如 ["直筒裤","休闲","高腰"]' AFTER `seasons`;


-- ============================================================================
-- 第 2 步：材质成分放宽到 128，并把 AI 独占列改名归位到 ai_ 前缀
--   （CHANGE 会保留数据；这里三列本就全空，等价于纯改名）
-- ============================================================================
ALTER TABLE `creator_item`
    MODIFY COLUMN `material` VARCHAR(128) NULL
        COMMENT '【创作者】材质成分，如 棉95%/氨纶5%',
    CHANGE COLUMN `color_hex` `ai_color_hex` VARCHAR(16) NULL
        COMMENT '【AI】主色 HEX，如 #RRGGBB',
    CHANGE COLUMN `style_tags` `ai_styles` VARCHAR(512) NULL
        COMMENT '【AI】风格/款式标签，JSON数组（与创作者的 styles 各存一份，不合并）',
    CHANGE COLUMN `selling_points` `ai_selling_points` VARCHAR(255) NULL
        COMMENT '【AI】推广卖点文案（限40字）';


-- ============================================================================
-- 第 3 步：新增 AI 专属列（AFTER 目标在上一句里已就位）
-- ============================================================================
ALTER TABLE `creator_item`
    ADD COLUMN `ai_product_name`    VARCHAR(128)  NULL
        COMMENT '【AI】商品名，推广用，与作品标题 title 解耦' AFTER `anchor_json`,
    ADD COLUMN `ai_color_name`      VARCHAR(32)   NULL
        COMMENT '【AI】主色中文名，如 浅灰蓝' AFTER `ai_color_hex`,
    ADD COLUMN `ai_material`        VARCHAR(128)  NULL
        COMMENT '【AI】材质判定（与创作者的 material 各存一份，不合并）' AFTER `ai_styles`,
    ADD COLUMN `ai_suggested_price` DECIMAL(10,2) NULL
        COMMENT '【AI】建议零售价（元），仅供参考，与实际售价 price 无关' AFTER `ai_selling_points`;


-- ============================================================================
-- 第 4 步：补两个「依赖第 3 步新增列」位置的 AI 列
-- ============================================================================
ALTER TABLE `creator_item`
    ADD COLUMN `ai_category_code` VARCHAR(32) NULL
        COMMENT '【AI】判定的一级分类码 TOP/BOTTOM/SET/SHOES/BAG/ACCESSORY/DECOR/OTHER（创作者选的分类另存 category_code）' AFTER `ai_product_name`,
    ADD COLUMN `ai_seasons` VARCHAR(64) NULL
        COMMENT '【AI】判定季节，JSON数组（与创作者的 seasons 各存一份，不合并）' AFTER `ai_color_name`;


-- ============================================================================
-- 第 5 步（可选）：索引，供「解析失败补偿」定时任务扫描未解析作品
-- ============================================================================
ALTER TABLE `creator_item`
    ADD INDEX `idx_ai_parsed` (`ai_parsed`);


-- ============================================================================
-- 【执行后校验】
-- ============================================================================
-- 1) 断言 ai_ 前缀列共 13 个（含原有的 ai_parsed / ai_model / ai_parsed_at）
-- SELECT COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA='trybe' AND TABLE_NAME='creator_item' AND COLUMN_NAME LIKE 'ai\_%'
--  ORDER BY ORDINAL_POSITION;
--
-- 2) 反向断言：不应再有旧名（预期 0 行）
-- SELECT COLUMN_NAME FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA='trybe' AND TABLE_NAME='creator_item'
--    AND COLUMN_NAME IN ('color_hex','style_tags','selling_points','product_name','color_name','suggested_price');
--
-- 3) 数据未丢：行数应仍为 2，且这两行的 title/pattern_tags/topics/image_key 原样
-- SELECT id,title,pattern_tags,topics,image_key IS NOT NULL AS has_img FROM creator_item;


-- ============================================================================
-- 【回滚脚本】仅在改造后发现问题时执行
-- ============================================================================
/*
ALTER TABLE `creator_item`
    DROP INDEX `idx_ai_parsed`,
    DROP COLUMN `link_url`,
    DROP COLUMN `styles`,
    DROP COLUMN `ai_product_name`,
    DROP COLUMN `ai_category_code`,
    DROP COLUMN `ai_color_name`,
    DROP COLUMN `ai_seasons`,
    DROP COLUMN `ai_material`,
    DROP COLUMN `ai_suggested_price`,
    CHANGE COLUMN `ai_color_hex` `color_hex` VARCHAR(16) NULL COMMENT '主色 HEX，如 #RRGGBB',
    CHANGE COLUMN `ai_styles` `style_tags` VARCHAR(255) NULL COMMENT '风格标签，JSON数组',
    CHANGE COLUMN `ai_selling_points` `selling_points` VARCHAR(255) NULL COMMENT '推广卖点文案（限40字）',
    MODIFY COLUMN `material` VARCHAR(64) NULL COMMENT '材质';
*/


-- ============================================================================
-- 【必须同步的代码改动】库改完但代码不改，等于白改
-- ----------------------------------------------------------------------------
-- 1. creator-service / domain/po/CreatorItem.java
--    - 新增：linkUrl、styles（创作者）
--    - 新增：aiProductName、aiCategoryCode、aiColorName、aiSeasons、aiMaterial、aiSuggestedPrice
--    - 改名：colorHex→aiColorHex、styleTags→aiStyles、sellingPoints→aiSellingPoints
--    - seasons、material 保持原名（语义归创作者）
--    - 删除：productName、colorName、suggestedPrice 这三个从未建列的字段（若已写进 PO）
--
-- 2. creator-service / domain/dto/ItemSaveReq.java（创作者可写白名单）
--    新增：linkUrl、seasons(List<String>)、styles(List<String>)、material
--
-- 3. creator-service / domain/vo/ItemDetailVO.java（创作者可见）
--    新增：linkUrl、seasons、styles、material
--    **不得**出现任何 ai_ 字段（只保留 aiParsed 状态位）
--
-- 4. creator-service / service/impl/CreatorItemServiceImpl.java
--    - createItem / updateItem 的白名单补上 linkUrl / seasons / styles / material
--    - 注意：分类不再由 AI 覆盖，createItem 里 zone→categoryCode 的兜底映射成为唯一来源
--
-- 5. creator-service / service/impl/GarmentInfoFillServiceImpl.java（关键）
--    - 全部写入目标改为 ai_ 列：aiProductName / aiCategoryCode / aiColorHex /
--      aiColorName / aiSeasons / aiStyles / aiMaterial / aiSellingPoints / aiSuggestedPrice
--    - **停止**写 categoryCode、material、seasons、styleTags —— 这四处正是与创作者字段冲突的地方
--    - 截断长度同步新列宽：ai_material 128、ai_styles 512、ai_product_name 128
--
-- 6. creator-service / domain/dto/ItemPageQuery.java
--    keyword 检索可加入 seasons / styles；**不得**加入任何 ai_ 列（否则创作者能二分反推 AI 结果）
--
-- 7. 前端 D:\trybeHtml
--    - types/item.ts：ItemSavePayload / ItemDetailVO 同步增删字段（同样不得出现 ai_ 字段）
--    - pages/publish/PublishPage.tsx + pages/items/components/ItemFormFields.tsx：
--      增加「链接 / 季节 / 衣服款式 / 材质成分」输入项
--    - pages/publish/publishOptions.ts：SEASON_OPTIONS 已有（SPRING/SUMMER/AUTUMN/WINTER），
--      需为「衣服款式」新增候选词常量
--    - 图片增删：接 ModelImageController 的 M1/M2/M3 三个接口；
--      若放开「1 或 4 张」限制，需同步 ALLOWED_IMAGE_COUNTS
--    - 服务端待补：删除主图时自动改选剩余第一张（否则 image_key 会指向已删图）
-- ============================================================================
