package com.trybe.bodyprofile.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trybe.bodyprofile.domain.dto.UpsertProfileReq;
import com.trybe.bodyprofile.domain.po.BodyProfile;
import com.trybe.bodyprofile.domain.vo.BodyProfileVO;

public interface IBodyProfileService extends IService<BodyProfile> {

    /**
     * 获取我的档案（含关联素材列表）
     *
     * @param userId 当前用户ID
     * @return 档案VO，不存在返回 null
     */
    BodyProfileVO getMyProfile(Long userId);

    /**
     * 保存我的档案身型数据（不存在则创建）
     *
     * @param userId 当前用户ID
     * @param req    身型数据请求
     * @return 保存后的档案VO
     */
    BodyProfileVO upsertBody(Long userId, UpsertProfileReq req);

    /**
     * 关联素材到我的档案（档案不存在则先创建）
     *
     * @param userId     当前用户ID
     * @param materialId 素材ID
     * @return 关联后的档案VO
     */
    BodyProfileVO linkMaterial(Long userId, Long materialId);

    /**
     * 解除素材与档案的关联
     *
     * @param userId     当前用户ID
     * @param materialId 素材ID
     */
    void unlinkMaterial(Long userId, Long materialId);

    /**
     * 设置素材为主素材（同类型其他素材 is_primary 置 0）
     *
     * @param userId     当前用户ID
     * @param materialId 素材ID
     * @return 更新后的档案VO
     */
    BodyProfileVO setPrimaryMaterial(Long userId, Long materialId);
}
