package com.trybe.creator.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trybe.common.utils.BeanUtils;
import com.trybe.common.utils.CollUtils;
import com.trybe.creator.domain.enums.AuditAction;
import com.trybe.creator.domain.po.AuditRecord;
import com.trybe.creator.domain.vo.AuditRecordVO;
import com.trybe.creator.mapper.AuditRecordMapper;
import com.trybe.creator.service.IAuditRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审核流水服务实现
 *
 * <p>流水写入 span==纯 DB 操作：允许加事务（由调用方的事务边界管理），
 * 本身不产生外部交互，因此这里标注 @Transactional 传播到调用方事务中是安全的。
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditRecordServiceImpl extends ServiceImpl<AuditRecordMapper, AuditRecord>
        implements IAuditRecordService {

    private static final String DEFAULT_OBJECT_TYPE = "CREATOR_ITEM";

    @Override
    public void record(String objectType, Long objectId, AuditAction action, Long operatorId, String reason) {
        AuditRecord record = new AuditRecord()
                .setObjectType(objectType == null ? DEFAULT_OBJECT_TYPE : objectType)
                .setObjectId(objectId)
                .setAction(action == null ? null : action.getCode())
                .setOperatorId(operatorId)
                .setReason(reason);
        save(record);
        log.info("[audit] 流水已记录 objectType={} objectId={} action={} operatorId={}",
                record.getObjectType(), objectId, action, operatorId);
    }

    @Override
    public List<AuditRecordVO> listByObject(String objectType, Long objectId) {
        List<AuditRecord> records = list(new LambdaQueryWrapper<AuditRecord>()
                .eq(AuditRecord::getObjectType, objectType == null ? DEFAULT_OBJECT_TYPE : objectType)
                .eq(AuditRecord::getObjectId, objectId)
                .orderByDesc(AuditRecord::getCreateTime)
                .orderByDesc(AuditRecord::getId));
        if (CollUtils.isEmpty(records)) {
            return CollUtils.emptyList();
        }
        return BeanUtils.copyList(records, AuditRecordVO.class);
    }
}
