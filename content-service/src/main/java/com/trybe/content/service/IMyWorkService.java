package com.trybe.content.service;

import com.trybe.api.dto.MyWorkBriefDTO;
import com.trybe.api.dto.PageResult;

/**
 * 小程序「我的作品」服务
 *
 * <p>与 {@link IWorkService}（公开浏览）的关键差别：<b>这里不静默降级</b>。
 * 浏览页挂掉返回空列表还能算「暂时没内容」，但「我的作品」挂掉返回空列表，
 * 用户会以为自己的作品被删了 —— 所以上游异常必须原样抛出去，让页面显示可重试的错误态。
 *
 * @author TryBe Team
 */
public interface IMyWorkService {

    /**
     * 分页查询当前登录用户的作品
     *
     * @param status   ON_SHELF / OFF_SHELF（草稿不在 C 端开放）
     * @param pageNo   页码，从 1 开始
     * @param pageSize 每页数量
     */
    PageResult<MyWorkBriefDTO> pageMyWorks(String status, Integer pageNo, Integer pageSize);

    /**
     * 上架（仅已下架可上架）
     */
    void onShelf(Long id);

    /**
     * 下架（仅已上架可下架）
     */
    void offShelf(Long id);
}
