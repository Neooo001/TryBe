package com.trybe.creator.service;

import com.trybe.api.dto.WorkBriefDTO;
import com.trybe.api.dto.WorkDetailDTO;
import com.trybe.api.dto.WorkQuery;
import com.trybe.common.domain.PageDTO;

import java.util.List;

/**
 * 作品公开读服务（供 C 端 content-service 经 Feign 调用）
 *
 * <p>职责边界：<b>只读、只出已上架作品、只出创作者公开发的内容</b>。
 * 不含任何写操作，不含创作者私有的状态机与草稿数据。
 *
 * <p>数据出口唯一入口是 {@code /internal/creator/**}（服务间调用，网关无此路由），
 * 因此本服务不需要也不应该做「当前登录用户」校验 —— 它只回答「哪些作品对所有人可见」。
 */
public interface IPublicWorkReadService {

    /**
     * 分页查询已上架的公开作品（按发布时间倒序）
     *
     * @param query 分区 / 关键词 / 分页（null 视为默认第一页）
     */
    PageDTO<WorkBriefDTO> pagePublicWorks(WorkQuery query);

    /**
     * 查询单个已上架作品的公开详情（小程序作品详情页）
     *
     * @param id 作品ID
     * @return 作品详情；作品不存在 / 未上架 / 已逻辑删除时返回 {@code null}
     */
    WorkDetailDTO getPublicWorkDetail(Long id);

    /**
     * 作品分区字典
     */
    List<String> listZones();
}
