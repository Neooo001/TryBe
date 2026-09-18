package com.trybe.creator.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trybe.creator.domain.enums.CreatorItemStatus;
import com.trybe.creator.domain.po.CreatorItem;
import com.trybe.creator.domain.vo.AdminStatsVO;
import com.trybe.creator.domain.vo.CreatorStatsVO;
import com.trybe.creator.mapper.CreatorItemMapper;
import com.trybe.creator.mapper.UserReadMapper;
import com.trybe.creator.service.IStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 数据看板服务实现
 *
 * <p>统计口径：全部排除逻辑删除行（MyBatis-Plus @TableLogic 自动生效）；
 * 「今日新增」以 create_time >= 今日零点为界。
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsServiceImpl extends ServiceImpl<CreatorItemMapper, CreatorItem> implements IStatsService {

    private final UserReadMapper userReadMapper;

    /**
     * S1 创作者我的看板
     */
    @Override
    public CreatorStatsVO creatorOverview(Long creatorId) {
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();

        CreatorStatsVO vo = new CreatorStatsVO();
        vo.setTotalItems(countLong(new LambdaQueryWrapper<CreatorItem>()
                .eq(CreatorItem::getCreatorId, creatorId)));
        vo.setDraft(countByStatus(creatorId, CreatorItemStatus.DRAFT));
        vo.setPending(countByStatus(creatorId, CreatorItemStatus.PENDING));
        vo.setApproved(countByStatus(creatorId, CreatorItemStatus.APPROVED));
        vo.setRejected(countByStatus(creatorId, CreatorItemStatus.REJECTED));
        vo.setOnShelf(countByStatus(creatorId, CreatorItemStatus.ON_SHELF));
        vo.setOffShelf(countByStatus(creatorId, CreatorItemStatus.OFF_SHELF));
        vo.setTodayNew(countLong(new LambdaQueryWrapper<CreatorItem>()
                .eq(CreatorItem::getCreatorId, creatorId)
                .ge(CreatorItem::getCreateTime, dayStart)));
        return vo;
    }

    /**
     * S2 运营全局看板
     */
    @Override
    public AdminStatsVO adminOverview() {
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        AdminStatsVO vo = new AdminStatsVO();
        vo.setTotalCreators(safeCountCreators());
        vo.setTotalItems(countLong(null));
        vo.setPendingItems(countLong(new LambdaQueryWrapper<CreatorItem>()
                .eq(CreatorItem::getStatus, CreatorItemStatus.PENDING.getCode())));
        vo.setTodayNewItems(countLong(new LambdaQueryWrapper<CreatorItem>()
                .ge(CreatorItem::getCreateTime, dayStart)));
        vo.setTodayNewApplications(safeCountTodayApplications(dayStart));
        return vo;
    }

    /**
     * MyBatis-Plus 3.4.2 的 count() 返回 int，这里统一装箱为 Long（看板 VO 用 Long，便于前端空值处理）
     */
    private Long countLong(LambdaQueryWrapper<CreatorItem> wrapper) {
        return (long) count(wrapper);
    }

    private long countByStatus(Long creatorId, CreatorItemStatus status) {
        return count(new LambdaQueryWrapper<CreatorItem>()
                .eq(CreatorItem::getCreatorId, creatorId)
                .eq(CreatorItem::getStatus, status.getCode()));
    }

    private Long safeCountCreators() {
        try {
            Long count = userReadMapper.countCreators();
            return count == null ? 0L : count;
        } catch (Exception e) {
            log.warn("[stats] 统计创作者总数失败，降级为 0", e);
            return 0L;
        }
    }

    private Long safeCountTodayApplications(LocalDateTime dayStart) {
        try {
            Long count = userReadMapper.countTodayApplications(dayStart);
            return count == null ? 0L : count;
        } catch (Exception e) {
            log.warn("[stats] 统计今日入驻申请数失败，降级为 0", e);
            return 0L;
        }
    }
}
