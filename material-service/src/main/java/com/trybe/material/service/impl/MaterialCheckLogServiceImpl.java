package com.trybe.material.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BadRequestException;
import com.trybe.material.domain.enums.CheckStatus;
import com.trybe.material.domain.enums.CheckType;
import com.trybe.material.domain.po.MaterialCheckLog;
import com.trybe.material.mapper.MaterialCheckLogMapper;
import com.trybe.material.service.IMaterialCheckLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * <p>
 * 素材校验日志服务实现
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialCheckLogServiceImpl extends ServiceImpl<MaterialCheckLogMapper, MaterialCheckLog> implements IMaterialCheckLogService {

    @Override
    public Long logCheckStart(Long materialId, String checkType) {
        MaterialCheckLog checkLog = new MaterialCheckLog();
        checkLog.setMaterialId(materialId);
        checkLog.setCheckType(CheckType.of(checkType));
        checkLog.setStatus(CheckStatus.PROCESSING);
        checkLog.setStartTime(LocalDateTime.now());
        save(checkLog);

        log.info("[MaterialCheckLog] 校验开始 logId={} materialId={} checkType={}",
                checkLog.getId(), materialId, checkType);
        return checkLog.getId();
    }

    @Override
    public void logCheckResult(Long logId, boolean passed, String resultDetail, long costMs) {
        MaterialCheckLog checkLog = getById(logId);
        if (checkLog == null) {
            throw new BadRequestException(ErrorCodeEnum.DATA_NOT_FOUND, "校验日志不存在");
        }

        checkLog.setStatus(passed ? CheckStatus.PASSED : CheckStatus.FAILED);
        checkLog.setResultDetail(resultDetail);
        checkLog.setCostMs(costMs);
        checkLog.setEndTime(LocalDateTime.now());
        updateById(checkLog);

        log.info("[MaterialCheckLog] 校验完成 logId={} materialId={} passed={} costMs={}",
                logId, checkLog.getMaterialId(), passed, costMs);
    }
}
