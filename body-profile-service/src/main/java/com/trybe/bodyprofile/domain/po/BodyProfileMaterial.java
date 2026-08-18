package com.trybe.bodyprofile.domain.po;

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
 * 档案-素材中间表（档案 1:N 素材，形象 FACE / 手部 HAND 均通过本表关联）
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("body_profile_material")
public class BodyProfileMaterial implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 档案ID
     */
    private Long profileId;

    /**
     * 素材ID（material 表）
     */
    private Long materialId;

    /**
     * 素材类型：FACE-人脸, HAND-手部（冗余，避免跨服务联表）
     */
    private String materialType;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 是否主图：0-否, 1-是
     */
    private Integer isPrimary;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
