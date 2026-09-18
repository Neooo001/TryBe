package com.trybe.wardrobe.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 衣服导入异步任务
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("wardrobe_import_task")
public class WardrobeImportTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 任务业务号（雪花ID字符串），同时作为 OSS 目录名 */
    private String taskNo;

    private Long userId;

    /** 导入来源 */
    private String source;

    /** 原图 OSS objectKey */
    private String originObjectKey;

    /** AI 裁剪图 OSS objectKey */
    private String cutoutObjectKey;

    /** 0-待处理, 1-处理中, 2-成功, 3-失败 */
    private Integer status;

    /** 生成的衣柜条目ID */
    private Long wardrobeId;

    private String aiModel;

    /** AI 识别结果JSON（结构化属性） */
    private String aiResultJson;

    /** AI 原始返回JSON */
    private String aiRawJson;

    private String failReason;

    private Integer retryCount;

    private Long costMs;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
