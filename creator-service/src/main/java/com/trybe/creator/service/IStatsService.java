package com.trybe.creator.service;

import com.trybe.creator.domain.vo.AdminStatsVO;
import com.trybe.creator.domain.vo.CreatorStatsVO;

/**
 * 数据看板服务（S1 创作者 / S2 运营）
 *
 * @author TryBe Team
 */
public interface IStatsService {

    /**
     * S1 创作者我的看板（按状态统计 + 今日新增）
     *
     * @param creatorId 创作者用户ID
     * @return 看板数据
     */
    CreatorStatsVO creatorOverview(Long creatorId);

    /**
     * S2 运营全局看板
     *
     * @return 看板数据
     */
    AdminStatsVO adminOverview();
}
