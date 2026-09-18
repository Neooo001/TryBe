package com.trybe.creator.service;

import com.trybe.common.domain.PageDTO;
import com.trybe.creator.domain.dto.ItemPageQuery;
import com.trybe.creator.domain.dto.ItemSaveReq;
import com.trybe.creator.domain.dto.ReviewReq;
import com.trybe.creator.domain.vo.ItemDetailVO;
import com.trybe.creator.domain.vo.ItemListVO;

/**
 * 创作者物品服务（I1~I8 创作者侧 + R1~R3 运营侧复用同一套业务对象）
 *
 * @author TryBe Team
 */
public interface ICreatorItemService {

    /**
     * I1 我的物品分页列表
     */
    PageDTO<ItemListVO> pageMyItems(Long creatorId, ItemPageQuery query);

    /**
     * I2 我的物品详情（非本人返回 402）
     */
    ItemDetailVO getMyItem(Long creatorId, Long id);

    /**
     * I3 新建物品（DRAFT）
     */
    ItemDetailVO createItem(Long creatorId, ItemSaveReq req);

    /**
     * I4 编辑物品（字段为 null 表示不修改）
     */
    ItemDetailVO updateItem(Long creatorId, Long id, ItemSaveReq req);

    /**
     * I5 逻辑删除物品
     */
    void deleteItem(Long creatorId, Long id);

    /**
     * I6 提交审核（DRAFT/REJECTED → PENDING，写 SUBMIT 流水）
     */
    void submit(Long creatorId, Long id);

    /**
     * I7 上架（APPROVED/OFF_SHELF → ON_SHELF）
     */
    void onShelf(Long creatorId, Long id);

    /**
     * I8 下架（ON_SHELF → OFF_SHELF）
     */
    void offShelf(Long creatorId, Long id);

    /**
     * R1 运营待审队列
     */
    PageDTO<ItemListVO> pagePendingItems(ItemPageQuery query);

    /**
     * R2 运营审核详情（含创作者昵称与模特图质检明细）
     */
    ItemDetailVO getAdminItem(Long id);

    /**
     * R3 物品审核（PENDING → APPROVED/REJECTED，写 APPROVE/REJECT 流水）
     */
    void review(Long adminId, Long id, ReviewReq req);
}
