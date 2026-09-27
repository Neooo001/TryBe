package com.trybe.creator.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 创作者个人主页档案表（{@code creator_profile}，与 {@code web_user} 1:1）
 *
 * <p>字段分两段：
 * <ol>
 *   <li>公开主页资料（创作者可编辑）— nickname / avatarKey / coverKey / bio / fieldCodes</li>
 *   <li>平台治理字段（创作者只读）— homepageStatus（仅管理端可改）</li>
 * </ol>
 *
 * <p>账号资料（手机号/账号昵称/创建时间/最近登录时间）<b>不在本表</b>：
 * 由 {@code WebUserBrief} 对 {@code web_user} 做只读投影获取，避免双写漂移。
 *
 * <p>行记录懒创建：首次 {@code GET /creator/profile} 时按 web_user.nickname 兜底插入默认行。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("creator_profile")
public class CreatorProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 创作者用户ID（web_user.id，web域；与 wx_user 零关联） */
    private Long creatorId;

    /** 创作者昵称（公开主页展示名） */
    private String nickname;

    /** 头像 OSS objectKey（creator/{userId}/profile/avatar.{ext}；NULL=默认头像） */
    private String avatarKey;

    /** 主页封面图 OSS objectKey（creator/{userId}/profile/cover.{ext}；NULL=默认封面） */
    private String coverKey;

    /** 个人简介 */
    private String bio;

    /** 创作方向编码，逗号分隔 CreatorField：HAIR_COLOR,OUTFIT,...（最多5个） */
    private String fieldCodes;

    /** 主页展示状态：ENABLED/DISABLED（仅管理端可改，创作者接口只读） */
    private String homepageStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
