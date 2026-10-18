package com.trybe.content.service;

import com.trybe.api.dto.PageResult;
import com.trybe.api.dto.WorkBriefDTO;
import com.trybe.api.dto.WorkDetailDTO;
import com.trybe.api.dto.WorkQuery;

import java.util.List;

/**
 * C 端作品浏览服务
 *
 * <p>只做聚合与降级，不做业务归属判断：数据由 creator-service 提供，
 * 本服务负责把它变成「小程序能直接渲染的形状」。
 */
public interface IWorkService {

    /**
     * 分页查询公开作品（已上架，按发布时间倒序）
     */
    PageResult<WorkBriefDTO> pageWorks(WorkQuery query);

    /**
     * 单个作品的公开详情
     *
     * <p>与列表不同，这里<b>不做「静默降级为 null」</b>：详情页把「作品不存在」
     * 和「上游挂了」混为一谈，用户会以为作品被删了。两种结果分别抛
     * {@code DATA_NOT_FOUND} 与 {@code BIZ_ERROR}，由页面给出不同文案。
     *
     * @param id 作品ID
     */
    WorkDetailDTO getWorkDetail(Long id);

    /**
     * 作品分区字典（首页 / 圈子的筛选 tab）
     */
    List<String> listZones();
}
