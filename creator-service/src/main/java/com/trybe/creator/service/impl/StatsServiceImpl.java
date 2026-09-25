package com.trybe.creator.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trybe.creator.domain.enums.CreatorItemStatus;
import com.trybe.creator.domain.enums.GarmentCategoryL1;
import com.trybe.creator.domain.po.CreatorItem;
import com.trybe.creator.domain.po.CreatorItemModelImage;
import com.trybe.creator.domain.vo.CategoryStatVO;
import com.trybe.creator.domain.vo.CreatorStatsVO;
import com.trybe.creator.domain.vo.PublishTrendVO;
import com.trybe.creator.domain.vo.QcStatsVO;
import com.trybe.creator.domain.vo.TrendPointVO;
import com.trybe.creator.mapper.CreatorItemMapper;
import com.trybe.creator.mapper.CreatorItemModelImageMapper;
import com.trybe.creator.service.IStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据看板服务实现
 *
 * <p>统计口径：全部排除逻辑删除行（MyBatis-Plus @TableLogic 自动生效）；
 * 「今日新增」以 create_time >= 今日零点为界。
 *
 * <p>S1 共 6 次查询：物品总数 / 状态分布 / 今日新增 / 类目分布 / 模特图质检 / 近 30 天创建时间。
 * 全部走单列 select + Java 侧分组，无 JOIN、无 GROUP BY、不使用动态排序（规避 MP 3.4.2
 * concatOrderBy 解析失败静默丢 ORDER BY 的坑）。近 7 天由近 30 天结果切片而来，不二次查询。
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsServiceImpl extends ServiceImpl<CreatorItemMapper, CreatorItem> implements IStatsService {

    /** 趋势窗口天数：近 30 天 */
    private static final int TREND_DAYS_30 = 30;

    /** 趋势窗口天数：近 7 天（由 30 天结果末 7 条切片） */
    private static final int TREND_DAYS_7 = 7;

    private final CreatorItemModelImageMapper modelImageMapper;

    /**
     * S1 创作者我的看板
     */
    @Override
    public CreatorStatsVO creatorOverview(Long creatorId) {
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();

        // Q1 物品总数（独立 count，避免脏状态值导致求和少计）
        long totalItems = count(new LambdaQueryWrapper<CreatorItem>()
                .eq(CreatorItem::getCreatorId, creatorId));

        // Q2 状态分布：单列 select 后在 Java 侧分组
        Map<String, Long> statusCount = new HashMap<>();
        for (CreatorItem item : list(new LambdaQueryWrapper<CreatorItem>()
                .select(CreatorItem::getStatus)
                .eq(CreatorItem::getCreatorId, creatorId))) {
            CreatorItemStatus status = CreatorItemStatus.of(item.getStatus());
            if (status == null) {
                continue;
            }
            statusCount.merge(status.getCode(), 1L, Long::sum);
        }

        // Q3 今日新增
        long todayNew = count(new LambdaQueryWrapper<CreatorItem>()
                .eq(CreatorItem::getCreatorId, creatorId)
                .ge(CreatorItem::getCreateTime, dayStart));

        CreatorStatsVO vo = new CreatorStatsVO();
        vo.setTotalItems(totalItems);
        vo.setDraft(statusCount.getOrDefault(CreatorItemStatus.DRAFT.getCode(), 0L));
        vo.setOnShelf(statusCount.getOrDefault(CreatorItemStatus.ON_SHELF.getCode(), 0L));
        vo.setOffShelf(statusCount.getOrDefault(CreatorItemStatus.OFF_SHELF.getCode(), 0L));
        vo.setTodayNew(todayNew);
        vo.setCategoryStats(buildCategoryStats(creatorId, totalItems));
        vo.setQcStats(buildQcStats(creatorId));
        vo.setPublishTrend(buildPublishTrend(creatorId));
        return vo;
    }

    // ==================== 私有方法 ====================

    /**
     * MyBatis-Plus 3.4.2 的 count() 返回 int，这里统一装箱为 Long（看板 VO 用 Long，便于前端空值处理）
     */
    private Long countLong(LambdaQueryWrapper<CreatorItem> wrapper) {
        return (long) count(wrapper);
    }

    /**
     * Q4 类目分布：按 GarmentCategoryL1 枚举顺序恒返回 8 条，无数据补 0
     */
    private List<CategoryStatVO> buildCategoryStats(Long creatorId, long totalItems) {
        Map<String, Long> counter = new HashMap<>();
        for (CreatorItem item : list(new LambdaQueryWrapper<CreatorItem>()
                .select(CreatorItem::getCategoryCode)
                .eq(CreatorItem::getCreatorId, creatorId))) {
            if (item.getCategoryCode() == null || item.getCategoryCode().trim().isEmpty()) {
                continue;
            }
            counter.merge(item.getCategoryCode().trim(), 1L, Long::sum);
        }

        List<CategoryStatVO> stats = new ArrayList<>();
        for (GarmentCategoryL1 category : GarmentCategoryL1.values()) {
            long count = counter.getOrDefault(category.getCode(), 0L);
            CategoryStatVO vo = new CategoryStatVO();
            vo.setCategoryCode(category.getCode());
            vo.setCategoryLabel(category.getDesc());
            vo.setCount(count);
            vo.setRatio(percentOf(count, totalItems));
            stats.add(vo);
        }
        return stats;
    }

    /**
     * Q5 模特图质检聚合：该创作者名下全部模特图（含草稿物品的），
     * creator_item_model_image 有冗余 creator_id 列，直接 eq 即可，无需 JOIN creator_item
     */
    private QcStatsVO buildQcStats(Long creatorId) {
        long pass = 0L;
        long warn = 0L;
        long fail = 0L;
        long pending = 0L;
        long total = 0L;

        List<CreatorItemModelImage> images = modelImageMapper.selectList(new LambdaQueryWrapper<CreatorItemModelImage>()
                .select(CreatorItemModelImage::getQcStatus)
                .eq(CreatorItemModelImage::getCreatorId, creatorId));
        if (images != null) {
            for (CreatorItemModelImage image : images) {
                String qcStatus = image.getQcStatus();
                if (qcStatus == null || qcStatus.trim().isEmpty()) {
                    continue;
                }
                total++;
                switch (qcStatus.trim().toUpperCase()) {
                    case "PASS":
                        pass++;
                        break;
                    case "WARN":
                        warn++;
                        break;
                    case "FAIL":
                        fail++;
                        break;
                    case "PENDING":
                        pending++;
                        break;
                    default:
                        break;
                }
            }
        }

        QcStatsVO vo = new QcStatsVO();
        vo.setTotal(total);
        vo.setPass(pass);
        vo.setWarn(warn);
        vo.setFail(fail);
        vo.setPending(pending);
        vo.setPassRate(percentOf(pass, total));
        return vo;
    }

    /**
     * Q6 发布趋势：只查一次近 30 天窗口，近 7 天由结果末 7 条切片而来
     */
    private PublishTrendVO buildPublishTrend(Long creatorId) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(TREND_DAYS_30 - 1);

        Map<String, Long> counter = new HashMap<>();
        for (CreatorItem item : list(new LambdaQueryWrapper<CreatorItem>()
                .select(CreatorItem::getCreateTime)
                .eq(CreatorItem::getCreatorId, creatorId)
                .ge(CreatorItem::getCreateTime, startDate.atStartOfDay()))) {
            if (item.getCreateTime() == null) {
                continue;
            }
            counter.merge(item.getCreateTime().toLocalDate().toString(), 1L, Long::sum);
        }

        List<TrendPointVO> last30Days = new ArrayList<>(TREND_DAYS_30);
        for (int i = 0; i < TREND_DAYS_30; i++) {
            String date = startDate.plusDays(i).toString();
            TrendPointVO point = new TrendPointVO();
            point.setDate(date);
            point.setCount(counter.getOrDefault(date, 0L));
            last30Days.add(point);
        }

        PublishTrendVO vo = new PublishTrendVO();
        vo.setLast30Days(last30Days);
        vo.setLast7Days(new ArrayList<>(last30Days.subList(TREND_DAYS_30 - TREND_DAYS_7, TREND_DAYS_30)));
        return vo;
    }

    /**
     * 占比/通过率计算：保留 1 位小数；分母为 0 直接返回 0.0（防除零）
     */
    private Double percentOf(long part, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(part * 100.0 / total)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
