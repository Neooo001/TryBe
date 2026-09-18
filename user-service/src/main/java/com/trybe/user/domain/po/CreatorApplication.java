package com.trybe.user.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 创作者入驻申请表（归属 user-service：用户 → 创作者的身份申请单）
 * </p>
 *
 * <p>一个用户仅一条记录（uk_user 唯一索引），驳回后在原记录上更新重提。
 * 无逻辑删除：申请单需要保留完整审核轨迹。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("creator_application")
public class CreatorApplication implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 申请用户ID */
    private Long userId;

    /** 店铺/品牌名 */
    private String shopName;

    /** 店铺简介 */
    private String shopIntro;

    /** 联系电话 */
    private String contactPhone;

    /** 资质图 OSS objectKey（营业执照等） */
    private String licenseImageKey;

    /** 经营类目，逗号分隔 GarmentCategoryL1：TOP,BOTTOM,... */
    private String categoryScope;

    /** 状态：PENDING待审/APPROVED通过/REJECTED驳回 */
    private String status;

    /** 驳回原因 */
    private String rejectReason;

    /** 审核人（运营 user.id） */
    private Long reviewerId;

    /** 审核时间 */
    private LocalDateTime reviewedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
