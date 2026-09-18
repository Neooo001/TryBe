package com.trybe.creator.service;

import com.trybe.creator.domain.enums.AuditAction;
import com.trybe.creator.domain.vo.AuditRecordVO;

import java.util.List;

/**
 * 审核流水服务（v1 仅记录 creator_item 的提交审核/审核通过/驳回/上下架）
 *
 * @author TryBe Team
 */
public interface IAuditRecordService {

    /**
     * 写一条流水
     *
     * @param objectType 对象类型（CREATOR_ITEM）
     * @param objectId   对象ID
     * @param action     动作
     * @param operatorId 操作人 user.id
     * @param reason     原因/备注，可为空
     */
    void record(String objectType, Long objectId, AuditAction action, Long operatorId, String reason);

    /**
     * 查询某对象的流水列表（按操作时间倒序）
     *
     * @param objectType 对象类型
     * @param objectId   对象ID
     * @return 流水列表
     */
    List<AuditRecordVO> listByObject(String objectType, Long objectId);
}
