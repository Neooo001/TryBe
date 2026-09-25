package com.trybe.creator.service;

import com.trybe.creator.domain.vo.CreatorStatsVO;

/**
 * 数据看板服务
 *
 * <p>单一角色化后仅保留「我的看板」，运营全局看板已下线。
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
}
