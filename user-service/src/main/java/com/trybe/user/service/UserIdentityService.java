package com.trybe.user.service;

import com.trybe.user.enums.IdentityType;

import java.util.List;

/**
 * 用户凭据服务 —— 「凭据与主体分离」的解析、匹配优先级与归属转移。
 *
 * <p>职责：
 * <ul>
 *   <li>按凭据类型解析用户（{@link #resolveUserIdByIdentity}）</li>
 *   <li>查询手机号当前持有者（{@link #resolveCurrentPhoneOwner}）</li>
 *   <li>查询手机号的历史持有者（{@link #findReleasedOwners}），支撑账号恢复路径</li>
 *   <li>按「当前持有者 → 历史持有者」优先级 + 密码校验解析用户（{@link #resolveUserIdByPhonePassword}）</li>
 *   <li>查询用户当前手机号（{@link #getCurrentPhone}）</li>
 *   <li>纯 DB 的归属转移事务（{@link #bindOrTransferInTx}）</li>
 * </ul>
 *
 * @author TryBe Team
 */
public interface UserIdentityService {

    /**
     * 按凭据解析用户ID。
     *
     * @param identityType 凭据类型
     * @param appId        应用标识（手机号传空串；openid 传小程序 appid）
     * @param identifier   凭据值（手机号 / openid）
     * @return userId；未命中返回 null
     */
    Long resolveUserIdByIdentity(IdentityType identityType, String appId, String identifier);

    /**
     * 查询手机号的当前持有者（user_identity 中 PHONE 凭据对应的 user_id）。
     *
     * @param phone 手机号
     * @return 当前持有者 userId；无人持有返回 null
     */
    Long resolveCurrentPhoneOwner(String phone);

    /**
     * 查询手机号的历史持有者（user_phone_history，按 released_time 倒序去重）。
     *
     * @param phone 手机号
     * @return 历史持有者 userId 列表（有序，最新释放的在前）；无则返回空列表
     */
    List<Long> findReleasedOwners(String phone);

    /**
     * 按「当前持有者 → 历史持有者」优先级 + 密码校验解析用户（§A.3.3）。
     *
     * <p>候选集合有序：① 当前持有者 B；② 历史持有者 A（按 released_time DESC 去重）。
     * 按序返回第一个 {@code user_password} 校验通过的账号；全部不通过返回 null。
     *
     * @param phone       手机号
     * @param rawPassword 明文密码
     * @return 命中的 userId；无匹配返回 null
     */
    Long resolveUserIdByPhonePassword(String phone, String rawPassword);

    /**
     * 查询用户当前绑定的手机号。
     *
     * @param userId 用户ID
     * @return 手机号；未绑定返回 null
     */
    String getCurrentPhone(Long userId);

    /**
     * 绑定或归属转移（D3，纯 DB 事务）。
     *
     * <p>规则：手机号空闲 → 直接绑定；已被自己占用 → 幂等返回；
     * 被他人占用 → 记录原持有者历史、释放其凭据、转移给当前用户。
     *
     * @param userId 绑定的用户ID（获得方 B）
     * @param phone  待绑定手机号
     */
    void bindOrTransferInTx(Long userId, String phone);

    /**
     * 注册手机号账号（纯 DB 事务）：创建 user + PHONE 凭据（可选写入密码）。
     *
     * <p>并发场景下另一事务先抢到该手机号时会抛 {@code DuplicateKeyException}，
     * 事务整体回滚（user 行一并回滚，不留孤儿），由调用方重新解析持有者。
     *
     * @param phone       手机号
     * @param role        角色（USER / CREATOR）
     * @param nickname    昵称（为空时用默认昵称）
     * @param rawPassword 明文密码；null 表示不设密码
     * @return 新建用户ID
     */
    Long registerPhoneUserInTx(String phone, String role, String nickname, String rawPassword);

    /**
     * 注册微信账号（纯 DB 事务）：创建 user（phone_state=NEVER_BOUND）+ WECHAT_OPENID 凭据。
     *
     * @param appId  小程序 appid
     * @param openid 微信 openid
     * @return 新建用户ID
     */
    Long registerWechatUserInTx(String appId, String openid);
}
