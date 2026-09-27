package com.trybe.creator.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * {@code web_user} 只读投影（个人中心账号资料段）
 *
 * <p><b>只读约定</b>：本实体只映射账号资料展示所需列，
 * <b>刻意不映射 {@code password_hash}</b>（查询 SQL 不会带出该列，杜绝密码哈希外泄），
 * 且 creator-service <b>禁止对 web_user 做任何写操作</b>（写入归属 user-service，
 * 与 {@code UserBrief} / CreatorApplication 注释中的共享库只读约定一致）。
 *
 * <p>手机号等敏感字段由服务层脱敏后（138****8000）才进入 VO 返回前端。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("web_user")
public class WebUserBrief implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 登录手机号（web域账号标识，唯一；展示时脱敏） */
    private String phone;

    /** 账号昵称（注册昵称，与 creator_profile.nickname 公开昵称相互独立） */
    private String nickname;

    /** 账号创建时间 */
    private LocalDateTime createTime;

    /** 最近登录时间 */
    private LocalDateTime lastLoginTime;
}
