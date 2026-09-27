package com.trybe.creator.service;

import com.trybe.creator.domain.dto.CreatorProfileUpdateReq;
import com.trybe.creator.domain.vo.CreatorProfileVO;

/**
 * 创作者个人中心服务（个人主页资料 + 账号资料只读投影）
 *
 * @author TryBe Team
 */
public interface ICreatorProfileService {

    /**
     * 获取当前创作者个人中心资料（档案行不存在时懒创建默认行）
     *
     * @param creatorId 当前创作者用户ID（必须来自 UserContext，禁止前端传入）
     * @return 公开主页资料 + 账号资料 + 创作方向字典
     */
    CreatorProfileVO getMyProfile(Long creatorId);

    /**
     * 更新当前创作者个人中心资料（白名单字段映射；头像/封面强校验归档位归属）
     *
     * @param creatorId 当前创作者用户ID（必须来自 UserContext，禁止前端传入）
     * @param req       更新请求
     * @return 更新后的完整资料
     */
    CreatorProfileVO updateMyProfile(Long creatorId, CreatorProfileUpdateReq req);
}
