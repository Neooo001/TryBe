package com.trybe.user.service;

import com.trybe.user.enums.IdentityType;
import com.trybe.user.enums.RegisterSource;

import java.util.List;

/**
 * 用户凭据服务 —— 「凭据与主体分离」的解析、归属判定与账号合并。
 *
 * <p>账号体系 v3 的核心规则（手机号为唯一身份锚点）：
 * <ul>
 *   <li>手机号只能通过短信验证码写入（{@link #bindPhoneInTx} / {@link #resolveOrCreateByPhoneInTx}）</li>
 *   <li>绑号时手机号已被<b>其他</b>账号持有 → 把当前账号<b>并入</b>持有者（数据随手机号走），
 *       而不是把手机号抢过来 —— 这是「沿用已有端数据」的落点</li>
 *   <li>注册/短信登录时手机号已存在 → 直接返回该账号，不新建</li>
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
     * 按「当前持有者 → 历史持有者」优先级 + 密码校验解析用户。
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
     * 该账号是否已设置密码（user_password 有行即视为已设置）。
     *
     * @param userId 用户ID
     * @return true=已设置
     */
    boolean hasPassword(Long userId);

    /**
     * 绑定手机号（纯 DB 事务）—— 小程序强制绑号的主入口。
     *
     * <p>三种分支：
     * <pre>
     *   手机号空闲        → 直接绑到 currentUserId
     *   已被自己持有      → 幂等，仅修正 phone_state
     *   已被其他账号持有  → 合并：currentUserId 并入持有者，凭据迁走、本行标记 MERGED
     * </pre>
     *
     * @param currentUserId 当前登录（新）账号
     * @param phone         待绑定手机号
     * @return 绑定结果（合并时 userId 变为原持有者，调用方需重新签发令牌）
     */
    BindResult bindPhoneInTx(Long currentUserId, String phone);

    /**
     * 按手机号「取或建」账号（纯 DB 事务）—— Web 注册 / 短信登录入口。
     *
     * <p>手机号已存在且账号未沉睡 → 直接返回该账号（沿用已有端数据，不新建）；
     * 不存在 → 新建账号（来源 = source，手机号直接置 BOUND）；
     * 存在但沉睡超 {@code DORMANT_DAYS} → 视为运营商二次放号，冻结旧账号并放行新建。
     *
     * @param phone    手机号（调用方必须已通过短信验证码校验）
     * @param source   注册来源（决定 register_source）
     * @param nickname 新建时的昵称（Web 端由调用方随机生成）
     * @return 取或建结果
     */
    EnsureResult ensureByPhoneInTx(String phone, RegisterSource source, String nickname);

    /**
     * 更新最近登录时间（单语句，自带原子性）。
     *
     * @param userId 用户ID
     */
    void touchLogin(Long userId);

    /**
     * 注册微信账号（纯 DB 事务）：创建账号（register_source=WX、phone_state=NEVER_BOUND）
     * + WECHAT_OPENID 凭据。
     *
     * @param appId  小程序 appid
     * @param openid 微信 openid
     * @return 新建用户ID
     */
    Long registerWechatUserInTx(String appId, String openid);

    /**
     * 绑定结果。
     */
    class BindResult {
        /** 绑定后的账号ID（合并时是原持有者ID） */
        private final Long userId;
        /** 是否发生了账号合并（true 表示登录身份已切换，必须重新签发令牌） */
        private final boolean merged;

        public BindResult(Long userId, boolean merged) {
            this.userId = userId;
            this.merged = merged;
        }

        public Long getUserId() {
            return userId;
        }

        public boolean isMerged() {
            return merged;
        }
    }

    /**
     * 「取或建」结果。
     */
    class EnsureResult {
        /** 账号ID */
        private final Long userId;
        /** 是否本次新建 */
        private final boolean created;

        public EnsureResult(Long userId, boolean created) {
            this.userId = userId;
            this.created = created;
        }

        public Long getUserId() {
            return userId;
        }

        public boolean isCreated() {
            return created;
        }
    }
}
