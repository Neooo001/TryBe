package com.trybe.user.service;

import com.trybe.common.domain.PageDTO;
import com.trybe.common.domain.PageQuery;
import com.trybe.user.domain.dto.CreatorApplyReq;
import com.trybe.user.domain.dto.LicensePresignReq;
import com.trybe.user.domain.dto.ReviewApplicationReq;
import com.trybe.user.domain.vo.ApplicationVO;
import com.trybe.user.domain.vo.PresignVO;

/**
 * 创作者入驻申请服务（U2~U6）
 *
 * <p>入驻与角色属用户身份问题，全部在 user-service 内闭环：
 * 申请单写 creator_application，审核通过写 user.role=CREATOR，
 * 并删除 creator-service 侧的角色缓存 {@code creator:role:{userId}}。
 *
 * @author TryBe Team
 */
public interface ICreatorApplyService {

    /**
     * U2：入驻资质图预签名（重点,v1 用户已登录即可申请）
     *
     * @param userId 当前登录用户ID（来自 UserContext，禁止前端传）
     * @param req    扩展名请求
     * @return 预签名直传信息
     */
    PresignVO presignLicense(Long userId, LicensePresignReq req);

    /**
     * U3：提交入驻申请（每用户一条，驳回后可原记录重提）
     *
     * @param userId 当前登录用户ID
     * @param req    入驻表单
     * @return 申请详情
     */
    ApplicationVO apply(Long userId, CreatorApplyReq req);

    /**
     * U4：查询我的入驻申请
     *
     * @param userId 当前登录用户ID
     * @return 申请详情；无记录返回 null
     */
    ApplicationVO getMyApplication(Long userId);

    /**
     * U5：运营审核列表（含申请人昵称）
     *
     * @param adminId 运营用户ID（内部校验 ADMIN 角色）
     * @param status  申请状态，可空
     * @param query   分页参数
     * @return 分页申请列表
     */
    PageDTO<ApplicationVO> pageApplications(Long adminId, String status, PageQuery query);

    /**
     * U6：入驻审核（通过 → user.role=CREATOR + 删角色缓存；驳回 → 记录原因）
     *
     * @param adminId 运营用户ID
     * @param id      申请单ID
     * @param req     审核结论
     */
    void reviewApplication(Long adminId, Long id, ReviewApplicationReq req);
}
