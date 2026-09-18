package com.trybe.wardrobe.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 衣服导入异步处理消息
 *
 * <p>只携带定位信息（taskId + taskNo + objectKey），不传图片二进制，
 * 避免消息体过大拖慢 Broker；消费者按需从 OSS 拉取原图。
 */
@Data
public class WardrobeImportMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息唯一ID，用于日志追踪与幂等观测 */
    private String messageId;

    /** 导入任务ID（wardrobe_import_task.id） */
    private Long taskId;

    /** 任务业务号，消费者可直接拼 OSS 目录 */
    private String taskNo;

    /** 用户ID */
    private Long userId;

    /** 原图 objectKey */
    private String originObjectKey;

    /** 已重试次数，用于限制 requeue 次数 */
    private Integer retryCount;
}
