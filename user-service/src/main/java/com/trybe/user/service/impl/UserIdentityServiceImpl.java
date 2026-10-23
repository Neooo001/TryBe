package com.trybe.user.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BizIllegalException;
import com.trybe.user.domain.po.PhoneTransferLog;
import com.trybe.user.domain.po.User;
import com.trybe.user.domain.po.UserIdentity;
import com.trybe.user.domain.po.UserPassword;
import com.trybe.user.domain.po.UserPhoneHistory;
import com.trybe.user.enums.IdentityType;
import com.trybe.user.enums.PhoneState;
import com.trybe.user.enums.RegisterSource;
import com.trybe.user.enums.UserStatus;
import com.trybe.user.mapper.AccountProbeMapper;
import com.trybe.user.mapper.PhoneTransferLogMapper;
import com.trybe.user.mapper.UserIdentityMapper;
import com.trybe.user.mapper.UserMapper;
import com.trybe.user.mapper.UserPasswordMapper;
import com.trybe.user.mapper.UserPhoneHistoryMapper;
import com.trybe.user.service.UserIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 用户凭据服务实现 —— 凭据解析、归属判定、账号合并（账号体系 v3）。
 *
 * <p><b>与 v2 最大的差别</b>：手机号被他人持有时，v2 是「抢号」（原持有者被置 RELEASED，
 * 新账号拿走手机号）；v3 是「归并」（新账号并入原持有者，数据随手机号走）。
 * 后者才是「用户也可以是创作者」的正确语义 —— 换一个微信登录，看到的应该是
 * 自己原来那套作品，而不是一个空账号。
 *
 * <p>MyBatis-Plus 3.4.2 注意：{@code one()} 多行命中会抛 {@code TooManyResultsException}，
 * 故所有「理论上唯一但由 DB 约束保证」的查询一律用 {@code list()} + 显式判定。
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserIdentityServiceImpl implements UserIdentityService {

    private static final String DEFAULT_NICKNAME = "小Try";

    /** 沉睡阈值（天）：超过该时长未登录，视为运营商二次放号，手机号可被新账号接管 */
    public static final long DORMANT_DAYS = 180L;

    /** 释放原因：被其他账号抢绑（v2 遗留，仅恢复路径使用） */
    private static final String RELEASE_REASON_TRANSFERRED = "PHONE_TRANSFERRED";
    /** 释放原因：沉睡账号被新机主接管 */
    private static final String RELEASE_REASON_DORMANT = "DORMANT_TAKEOVER";

    /** 转移场景：绑号 */
    private static final String SCENE_BIND = "BIND_PHONE";
    /** 转移场景：注册/短信登录按手机号取或建 */
    private static final String SCENE_REGISTER = "PHONE_REGISTER";
    /** 转移场景：沉睡接管 */
    private static final String SCENE_DORMANT = "DORMANT_TAKEOVER";
    /** 转移场景：账号合并 */
    private static final String SCENE_MERGE = "ACCOUNT_MERGE";

    private static final String PHONE_MASK_REGEX = "(\\d{3})\\d{4}(\\d{4})";
    private static final String PHONE_MASK_REPLACEMENT = "$1****$2";

    private final UserMapper userMapper;
    private final UserIdentityMapper userIdentityMapper;
    private final UserPasswordMapper userPasswordMapper;
    private final UserPhoneHistoryMapper userPhoneHistoryMapper;
    private final PhoneTransferLogMapper phoneTransferLogMapper;
    private final AccountProbeMapper accountProbeMapper;
    private final PasswordEncoder passwordEncoder;

    // ==================== 凭据解析 ====================

    @Override
    public Long resolveUserIdByIdentity(IdentityType identityType, String appId, String identifier) {
        if (identityType == null || StrUtil.isBlank(identifier)) {
            return null;
        }
        List<UserIdentity> list = userIdentityMapper.selectList(new LambdaQueryWrapper<UserIdentity>()
                .eq(UserIdentity::getIdentityType, identityType)
                .eq(UserIdentity::getAppId, StrUtil.nullToEmpty(appId))
                .eq(UserIdentity::getIdentifier, identifier));
        return list.isEmpty() ? null : list.get(0).getUserId();
    }

    @Override
    public Long resolveCurrentPhoneOwner(String phone) {
        return resolveUserIdByIdentity(IdentityType.PHONE, "", phone);
    }

    @Override
    public List<Long> findReleasedOwners(String phone) {
        if (StrUtil.isBlank(phone)) {
            return Collections.emptyList();
        }
        List<UserPhoneHistory> rows = userPhoneHistoryMapper.selectList(new LambdaQueryWrapper<UserPhoneHistory>()
                .eq(UserPhoneHistory::getPhone, phone)
                .orderByDesc(UserPhoneHistory::getReleasedTime));
        // 去重保序：同一用户可能有多条历史，仅保留最新一次
        List<Long> result = new ArrayList<>();
        for (UserPhoneHistory row : rows) {
            if (row.getUserId() != null && !result.contains(row.getUserId())) {
                result.add(row.getUserId());
            }
        }
        return result;
    }

    @Override
    public Long resolveUserIdByPhonePassword(String phone, String rawPassword) {
        if (StrUtil.isBlank(phone) || StrUtil.isBlank(rawPassword)) {
            return null;
        }
        // 候选集合有序：① 当前持有者 B；② 历史持有者 A（按 released_time DESC 去重）
        LinkedHashSet<Long> candidates = new LinkedHashSet<>();
        Long currentOwner = resolveCurrentPhoneOwner(phone);
        if (currentOwner != null) {
            candidates.add(currentOwner);
        }
        candidates.addAll(findReleasedOwners(phone));

        for (Long candidate : candidates) {
            UserPassword up = userPasswordMapper.selectById(candidate);
            if (up != null && StrUtil.isNotBlank(up.getPasswordHash())
                    && passwordEncoder.matches(rawPassword, up.getPasswordHash())) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public String getCurrentPhone(Long userId) {
        if (userId == null) {
            return null;
        }
        List<UserIdentity> list = userIdentityMapper.selectList(new LambdaQueryWrapper<UserIdentity>()
                .eq(UserIdentity::getUserId, userId)
                .eq(UserIdentity::getIdentityType, IdentityType.PHONE));
        return list.isEmpty() ? null : list.get(0).getIdentifier();
    }

    @Override
    public boolean hasPassword(Long userId) {
        if (userId == null) {
            return false;
        }
        UserPassword password = userPasswordMapper.selectById(userId);
        return password != null && StrUtil.isNotBlank(password.getPasswordHash());
    }

    // ==================== 绑定（小程序强制绑号） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BindResult bindPhoneInTx(Long currentUserId, String phone) {
        // 悲观锁：锁定该手机号的凭据行，串行化同号并发绑定
        List<UserIdentity> owners = lockPhoneIdentities(phone);
        LocalDateTime now = LocalDateTime.now();

        if (owners.isEmpty()) {
            // 手机号空闲 → 直接绑定
            insertPhoneIdentityOrThrow(currentUserId, phone, now);
            updatePhoneState(currentUserId, PhoneState.BOUND);
            writeTransferLog(null, currentUserId, phone, SCENE_BIND, now);
            log.info("[bindPhone] 手机号空闲，直接绑定 userId={} phone={}", currentUserId, maskPhone(phone));
            return new BindResult(currentUserId, false);
        }

        UserIdentity owner = owners.get(0);
        if (currentUserId.equals(owner.getUserId())) {
            // 幂等：已由本人持有。刻意保留 phone_state 自愈，修正历史脏数据
            updatePhoneState(currentUserId, PhoneState.BOUND);
            log.info("[bindPhone] 幂等：手机号已由本人持有 userId={} phone={}", currentUserId, maskPhone(phone));
            return new BindResult(currentUserId, false);
        }

        // ===== 关键分支：手机号属于另一个账号 → 当前账号并入对方 =====
        Long targetUserId = owner.getUserId();
        User target = requireUser(targetUserId);
        if (target.getStatus() == UserStatus.FROZEN) {
            throw new BizIllegalException(ErrorCodeEnum.ACCOUNT_FROZEN, "该手机号所属账号已被冻结，请联系客服");
        }
        // 安全阀：当前账号若已有业务数据，合并会静默丢数据 → 直接拒绝（正常情况下门禁保证其为空壳）
        assertNoBusinessData(currentUserId);

        // 1) 把当前账号的凭据（openid 等）全部迁到目标账号
        moveIdentities(currentUserId, targetUserId);
        // 2) 当前账号标记为已合并（保留行做审计，不物理删除）
        userMapper.updateById(new User()
                .setId(currentUserId)
                .setStatus(UserStatus.MERGED)
                .setMergedIntoUserId(targetUserId));
        // 3) 目标账号置为已绑定
        updatePhoneState(targetUserId, PhoneState.BOUND);
        // 4) 审计
        writeTransferLog(currentUserId, targetUserId, phone, SCENE_MERGE, now);

        log.info("[bindPhone] 账号已合并 新账号={} → 已有账号={} phone={}",
                currentUserId, targetUserId, maskPhone(phone));
        return new BindResult(targetUserId, true);
    }

    // ==================== 注册 / 短信登录（按手机号取或建） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnsureResult ensureByPhoneInTx(String phone, RegisterSource source, String nickname) {
        List<UserIdentity> owners = lockPhoneIdentities(phone);
        LocalDateTime now = LocalDateTime.now();

        if (owners.isEmpty()) {
            Long userId = createAccount(source, nickname, PhoneState.BOUND, now);
            insertPhoneIdentityOrThrow(userId, phone, now);
            writeTransferLog(null, userId, phone, SCENE_REGISTER, now);
            log.info("[ensureByPhone] 新账号建立 userId={} source={} phone={}",
                    userId, source, maskPhone(phone));
            return new EnsureResult(userId, true);
        }

        UserIdentity owner = owners.get(0);
        User existing = requireUser(owner.getUserId());

        // 沉睡账号（疑似运营商二次放号）→ 冻结旧账号、释放手机号，放行新账号
        if (isDormant(existing, now)) {
            userPhoneHistoryMapper.insert(new UserPhoneHistory()
                    .setUserId(existing.getId())
                    .setPhone(phone)
                    .setBoundTime(owner.getCreateTime())
                    .setReleasedTime(now)
                    .setReleaseReason(RELEASE_REASON_DORMANT));
            userIdentityMapper.deleteById(owner.getId());
            userMapper.updateById(new User().setId(existing.getId()).setStatus(UserStatus.FROZEN));

            Long userId = createAccount(source, nickname, PhoneState.BOUND, now);
            insertPhoneIdentityOrThrow(userId, phone, now);
            writeTransferLog(existing.getId(), userId, phone, SCENE_DORMANT, now);
            log.warn("[ensureByPhone] 沉睡账号已释放手机号 oldUserId={} newUserId={} phone={} lastLogin={}",
                    existing.getId(), userId, maskPhone(phone), existing.getLastLoginTime());
            return new EnsureResult(userId, true);
        }

        if (existing.getStatus() == UserStatus.FROZEN) {
            throw new BizIllegalException(ErrorCodeEnum.ACCOUNT_FROZEN, "账号已被冻结，请联系客服");
        }

        // 活跃占用 → 直接沿用（「Web 端已有数据则沿用 Web 端数据 / 小程序先注册则沿用小程序数据」）
        if (existing.getPhoneState() != PhoneState.BOUND) {
            updatePhoneState(existing.getId(), PhoneState.BOUND);
        }
        log.info("[ensureByPhone] 沿用已有账号 userId={} source={} phone={}",
                existing.getId(), existing.getRegisterSource(), maskPhone(phone));
        return new EnsureResult(existing.getId(), false);
    }

    @Override
    public void touchLogin(Long userId) {
        if (userId == null) {
            return;
        }
        userMapper.updateById(new User().setId(userId).setLastLoginTime(LocalDateTime.now()));
    }

    // ==================== 注册（微信建号） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long registerWechatUserInTx(String appId, String openid) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = createAccount(RegisterSource.WX, DEFAULT_NICKNAME, PhoneState.NEVER_BOUND, now);

        userIdentityMapper.insert(new UserIdentity()
                .setUserId(userId)
                .setIdentityType(IdentityType.WECHAT_OPENID)
                .setAppId(StrUtil.nullToEmpty(appId))
                .setIdentifier(openid)
                .setVerified(1)
                .setVerifiedTime(now)
                .setCreateTime(now)
                .setUpdateTime(now));

        log.info("[registerWechatUser] 微信建号成功 userId={}", userId);
        return userId;
    }

    // ==================== 私有方法 ====================

    /**
     * 创建账号主体（只建行，不写任何凭据）。
     */
    private Long createAccount(RegisterSource source, String nickname, PhoneState phoneState, LocalDateTime now) {
        User user = new User()
                .setNickname(StrUtil.blankToDefault(nickname, DEFAULT_NICKNAME))
                .setStatus(UserStatus.NORMAL)
                .setPhoneState(phoneState)
                .setRegisterSource(source)
                .setLastLoginTime(now)
                .setCreateTime(now)
                .setUpdateTime(now);
        userMapper.insert(user);
        return user.getId();
    }

    /**
     * 锁定某手机号的凭据行（串行化同号并发）。
     *
     * <p>命中时锁行、未命中时为间隙锁；两个事务同时未命中会各自拿到间隙锁，
     * 因此仍需 INSERT 的 {@link DuplicateKeyException} / 死锁异常兜底。
     */
    private List<UserIdentity> lockPhoneIdentities(String phone) {
        return userIdentityMapper.selectList(new LambdaQueryWrapper<UserIdentity>()
                .eq(UserIdentity::getIdentityType, IdentityType.PHONE)
                .eq(UserIdentity::getIdentifier, phone)
                .last("FOR UPDATE"));
    }

    /**
     * 把 from 账号的全部凭据迁到 to 账号。
     *
     * <p>唯一键 {@code (identity_type, app_id, identifier)} 只约束凭据本身、不约束 user_id，
     * 因此直接改 user_id 不会撞键；目标账号已有同类凭据也不影响（一人多个微信是允许的）。
     */
    private void moveIdentities(Long fromUserId, Long toUserId) {
        List<UserIdentity> identities = userIdentityMapper.selectList(new LambdaQueryWrapper<UserIdentity>()
                .eq(UserIdentity::getUserId, fromUserId));
        for (UserIdentity identity : identities) {
            identity.setUserId(toUserId);
            userIdentityMapper.updateById(identity);
        }
        log.info("[merge] 凭据迁移完成 from={} to={} count={}", fromUserId, toUserId, identities.size());
    }

    /**
     * 合并安全阀：当前账号若已产生业务数据则拒绝合并（宁可报错，不可静默丢数据）。
     */
    private void assertNoBusinessData(Long userId) {
        int items = accountProbeMapper.countCreatorItems(userId);
        int wardrobe = accountProbeMapper.countWardrobeItems(userId);
        if (items > 0 || wardrobe > 0) {
            log.error("[merge] 合并被拒：账号已有业务数据 userId={} creatorItems={} wardrobeItems={}",
                    userId, items, wardrobe);
            throw new BizIllegalException(ErrorCodeEnum.BIZ_ERROR,
                    "当前账号已有数据，无法并入该手机号，请联系客服处理");
        }
    }

    /**
     * 是否沉睡账号（最近活动距今超过 {@link #DORMANT_DAYS}）。
     */
    private boolean isDormant(User user, LocalDateTime now) {
        LocalDateTime lastActive = user.getLastLoginTime() != null
                ? user.getLastLoginTime() : user.getCreateTime();
        return lastActive != null && lastActive.isBefore(now.minusDays(DORMANT_DAYS));
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizIllegalException(ErrorCodeEnum.DATA_NOT_FOUND, "账号不存在");
        }
        return user;
    }

    /**
     * 插入 PHONE 凭据，并发冲突时抛 {@link ErrorCodeEnum#PHONE_OCCUPIED}（可重试）。
     *
     * <p>捕获范围刻意收敛为「明确的锁类 / 唯一键异常」三种，<b>绝不</b>捕获裸
     * {@code DataAccessException} —— 那会把真实 DB 故障也伪装成「稍后重试」，掩盖问题。
     */
    private void insertPhoneIdentityOrThrow(Long userId, String phone, LocalDateTime now) {
        try {
            userIdentityMapper.insert(new UserIdentity()
                    .setUserId(userId)
                    .setIdentityType(IdentityType.PHONE)
                    .setAppId("")
                    .setIdentifier(phone)
                    .setVerified(1)
                    .setVerifiedTime(now)
                    .setCreateTime(now)
                    .setUpdateTime(now));
        } catch (DuplicateKeyException e) {
            this.throwPhoneOccupied(phone, "唯一键冲突");
        } catch (DeadlockLoserDataAccessException e) {
            this.throwPhoneOccupied(phone, "死锁失败方");
        } catch (CannotAcquireLockException e) {
            this.throwPhoneOccupied(phone, "锁等待超时");
        }
    }

    /**
     * 并发绑定冲突的统一出口：记录告警并抛出可重试的 {@link ErrorCodeEnum#PHONE_OCCUPIED}（405）。
     */
    private void throwPhoneOccupied(String phone, String reason) {
        log.warn("[bindPhone] 并发冲突({})：手机号已被抢占 phone={}", reason, maskPhone(phone));
        throw new BizIllegalException(ErrorCodeEnum.PHONE_OCCUPIED, "操作冲突，请稍后重试");
    }

    /**
     * 更新用户手机号归属状态（仅写 phone_state，其余字段由 MP 的 NOT_NULL 策略忽略）。
     */
    private void updatePhoneState(Long userId, PhoneState state) {
        userMapper.updateById(new User().setId(userId).setPhoneState(state));
    }

    /**
     * 写归属转移审计日志（transfer_no 为幂等键）。
     */
    private void writeTransferLog(Long fromUserId, Long toUserId, String phone, String scene, LocalDateTime now) {
        phoneTransferLogMapper.insert(new PhoneTransferLog()
                .setTransferNo(IdUtil.fastSimpleUUID())
                .setPhone(phone)
                .setFromUserId(fromUserId)
                .setToUserId(toUserId)
                .setScene(scene)
                .setCreateTime(now));
    }

    /** 手机号脱敏：138****8000 */
    private String maskPhone(String phone) {
        if (StrUtil.isBlank(phone)) {
            return "***";
        }
        return phone.replaceAll(PHONE_MASK_REGEX, PHONE_MASK_REPLACEMENT);
    }
}
