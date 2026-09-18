package com.trybe.creator.service;

import com.trybe.creator.domain.dto.ModelImageBindReq;
import com.trybe.creator.domain.vo.ModelImageVO;

import java.util.List;

/**
 * 模特图服务（M1 绑定+同步质检 / M2 列表 / M3 删除）
 *
 * @author TryBe Team
 */
public interface IModelImageService {

    /**
     * M1 绑定模特图并同步质检
     *
     * <p>质检项：①尺寸下限 ≥512×512 ②宽高比 0.5~0.8（竖版） ③人脸数恰好 1。
     * 任一不过只标记 qcStatus + qcDetail，<b>不阻断保存</b>（红线⑥ 静默降级）。
     *
     * @param creatorId 创作者用户ID（来自 UserContext）
     * @param itemId    物品ID
     * @param req       绑定请求（含肖像授权确认）
     * @return 模特图视图对象（含质检结果）
     */
    ModelImageVO bind(Long creatorId, Long itemId, ModelImageBindReq req);

    /**
     * M2 我的物品模特图列表（归属校验后返回，按 sort_order、id 升序）
     *
     * @param creatorId 创作者用户ID
     * @param itemId    物品ID
     * @return 模特图列表
     */
    List<ModelImageVO> listMyItemImages(Long creatorId, Long itemId);

    /**
     * 物品模特图列表（内部使用：物品详情/审核详情组装，调用方已做归属校验）
     *
     * @param itemId 物品ID
     * @return 模特图列表
     */
    List<ModelImageVO> listByItem(Long itemId);

    /**
     * M3 逻辑删除模特图（仅本人）
     *
     * @param creatorId 创作者用户ID
     * @param imageId   模特图ID
     */
    void deleteImage(Long creatorId, Long imageId);

    /**
     * 统计物品下「已勾选肖像授权」的模特图数量（I6 提交审核前置校验使用）
     *
     * @param itemId 物品ID
     * @return 数量
     */
    long countAuthorized(Long itemId);
}
