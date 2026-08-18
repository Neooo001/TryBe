package com.trybe.material.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trybe.material.domain.po.MaterialCheckLog;

/**
 * <p>
 * 素材校验日志服务接口
 * </p>
 */
public interface IMaterialCheckLogService extends IService<MaterialCheckLog> {

    /**
     * 记录校验开始
     *
     * @param materialId 素材ID
     * @param checkType  校验类型
     * @return 日志ID
     */
    Long logCheckStart(Long materialId, String checkType);

    /**
     * 记录校验完成
     *
     * @param logId        日志ID
     * @param passed       是否通过
     * @param resultDetail 结果详情
     * @param costMs       耗时
     */
    void logCheckResult(Long logId, boolean passed, String resultDetail, long costMs);
}
