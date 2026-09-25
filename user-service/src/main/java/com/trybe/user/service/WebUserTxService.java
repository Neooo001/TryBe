package com.trybe.user.service;

import com.trybe.user.domain.dto.WebRegisterDTO;
import com.trybe.user.domain.po.WebUser;

/**
 * Web 域用户表的<b>纯 DB 事务</b>方法 —— 与 {@link WebAuthService} 分离，
 * 保持「含外部交互的方法不加 {@code @Transactional}」这一项目硬约定。
 *
 * @author TryBe Team
 */
public interface WebUserTxService {

    /**
     * 注册事务：占用判定（活跃拒绝 / 沉睡归档释放）→ 建号
     *
     * @param dto 注册请求
     * @return 新建用户
     */
    WebUser registerInTx(WebRegisterDTO dto);

    /**
     * 更新密码哈希
     *
     * @param userId        用户ID
     * @param passwordHash  新密码哈希（由调用方加密后传入）
     */
    void changePasswordHashInTx(Long userId, String passwordHash);
}
