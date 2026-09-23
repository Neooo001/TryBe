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
     * S1 创作者我的看板（KPI + 类目分布 + 质检通过率 + 发布趋势，一次返回）
     *
     * @param creatorId 创作者用户ID
     * @return 看板数据
     */
    CreatorStatsVO creatorOverview(Long creatorId);

    /**
     * S2 运营全局看板（创作者规模 + 物品规模 + 入驻申请处理情况）
     *
     * @return 看板数据
     */
    AdminStatsVO adminOverview();
}
