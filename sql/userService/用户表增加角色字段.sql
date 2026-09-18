USE trybe;

-- 创作者后台 v1：user 表增加角色字段
-- 角色取值：USER 普通用户（默认）/ CREATOR 创作者 / ADMIN 运营
ALTER TABLE `user`
    ADD COLUMN `role` VARCHAR(16) NOT NULL DEFAULT 'USER'
    COMMENT '角色：USER普通用户/CREATOR创作者/ADMIN运营' AFTER `status`;

-- 运营账号（v1 手动指定一个，把手机号换成实际运营账号的）
-- UPDATE `user` SET role = 'ADMIN' WHERE phone = '13800000000';
