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
import com.trybe.user.enums.UserStatus;
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
 * 用户凭据服务实现 —— 凭据解析、匹配优先级、归属转移。
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
    /** 释放原因：被其他账号抢绑 */
    private static final String RELEASE_REASON_TRANSFERRED = "PHONE_TRANSFERRED";
    /** 转移场景 */
    private static final String TRANSFER_SCENE_BIND = "BIND_PHONE";

    private static final String PHONE_MASK_REGEX = "(\\d{3})\\d{4}(\\d{4})";
    private static final String PHONE_MASK_REPLACEMENT = "$1****$2";

    private final UserMapper userMapper;
    private final UserIdentityMapper userIdentityMapper;
    private final UserPasswordMapper userPasswordMapper;
    private final UserPhoneHistoryMapper userPhoneHistoryMapper;
    private final PhoneTransferLogMapper phoneTransferLogMapper;
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

    // ==================== 归属转移 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindOrTransferInTx(Long userId, String phone) {
        // 悲观锁：锁定该手机号的凭据行，串行化同号并发绑定
        List<UserIdentity> owners = userIdentityMapper.selectList(new LambdaQueryWrapper<UserIdentity>()
                .eq(UserIdentity::getIdentityType, IdentityType.PHONE)
                .eq(UserIdentity::getIdentifier, phone)
                .last("FOR UPDATE"));
        LocalDateTime now = LocalDateTime.now();

        if (owners.isEmpty()) {
            // 手机号空闲 → 直接绑定
            insertPhoneIdentityOrThrow(userId, phone, now);
            updatePhoneState(userId, PhoneState.BOUND);
            writeTransferLog(null, userId, phone, now);
            log.info("[bindOrTransfer] 手机号空闲，直接绑定 userId={} phone={}", userId, maskPhone(phone));
            return;
        }

        UserIdentity owner = owners.get(0);
        if (userId.equals(owner.getUserId())) {
            // 幂等：已由本人持有。
            // ⚠️ 此处 phone_state=BOUND 的写入是【刻意保留】的自愈逻辑，请勿删除：
            // 历史脏数据可能存在「已持有 PHONE 凭据但 phone_state 陈旧（如误置 RELEASED/NEVER_BOUND）」
            // 的账号，借这次唯一的幂等绑定顺带把状态修正回来，避免后端凭据与前端提示长期不一致。
            updatePhoneState(userId, PhoneState.BOUND);
            log.info("[bindOrTransfer] 幂等：手机号已由本人持有 userId={} phone={}", userId, maskPhone(phone));
            return;
        }

        // 归属转移：原持有者 A → 当前用户 B
        Long fromUserId = owner.getUserId();
        // 1. 记录原持有者历史手机号（供恢复路径匹配）
        userPhoneHistoryMapper.insert(new UserPhoneHistory()
                .setUserId(fromUserId)
                .setPhone(phone)
                .setBoundTime(owner.getCreateTime())
                .setReleasedTime(now)
                .setReleaseReason(RELEASE_REASON_TRANSFERRED));
        // 2. 释放原持有者的 PHONE 凭据
        userIdentityMapper.deleteById(owner.getId());
        // 3. 原持有者置为「待补绑」（软提示）
        updatePhoneState(fromUserId, PhoneState.RELEASED);
        // 4. 绑定给当前用户（唯一键兜底，撞键抛 PHONE_OCCUPIED 并整体回滚）
        insertPhoneIdentityOrThrow(userId, phone, now);
        // 5. 当前用户置为「已绑定」
        updatePhoneState(userId, PhoneState.BOUND);
        // 6. 审计
        writeTransferLog(fromUserId, userId, phone, now);
        log.info("[bindOrTransfer] 归属转移完成 phone={} from={} to={}", maskPhone(phone), fromUserId, userId);
    }

    // ==================== 注册 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long registerWechatUserInTx(String appId, String openid) {
        LocalDateTime now = LocalDateTime.now();
        User user = new User()
                .setNickname(DEFAULT_NICKNAME)
                .setStatus(UserStatus.NORMAL)
                .setPhoneState(PhoneState.NEVER_BOUND)
                .setCreateTime(now)
                .setUpdateTime(now);
        userMapper.insert(user);

        userIdentityMapper.insert(new UserIdentity()
                .setUserId(user.getId())
                .setIdentityType(IdentityType.WECHAT_OPENID)
                .setAppId(StrUtil.nullToEmpty(appId))
                .setIdentifier(openid)
                .setVerified(1)
                .setVerifiedTime(now)
                .setCreateTime(now)
                .setUpdateTime(now));

        log.info("[registerWechatUser] 微信建号成功 userId={}", user.getId());
        return user.getId();
    }

    // ==================== 私有方法 ====================

    /**
     * 插入 PHONE 凭据，并发冲突时抛 {@link ErrorCodeEnum#PHONE_OCCUPIED}（可重试）。
     *
     * <p>捕获范围刻意收敛为「明确的锁类 / 唯一键异常」三种，<b>绝不</b>捕获裸
     * {@code DataAccessException} —— 那会把真实 DB 故障也伪装成「稍后重试」，掩盖问题。
     * <ul>
     *   <li>{@link DuplicateKeyException}：唯一键兜底（并发后到者撞键）。</li>
     *   <li>{@link DeadlockLoserDataAccessException}：空闲手机号并发绑定时，{@code FOR UPDATE}
     *       命中的是<b>间隙锁</b>，而间隙锁彼此兼容 —— 两个事务都能拿到锁并走到 INSERT，
     *       MySQL 可能返回<b>死锁</b>而非唯一键冲突；由死锁失败方抛出，属可重试冲突。</li>
     *   <li>{@link CannotAcquireLockException}：{@code FOR UPDATE} 锁等待超时
     *       （{@code innodb_lock_wait_timeout}），同样按可重试冲突处理。</li>
     * </ul>
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
     *
     * <p>与撞唯一键分支保持同一日志语义与对外文案（"操作冲突，请稍后重试"），
     * 使前端在任何一种并发冲突路径下都能得到一致的「可重试」行为。
     */
    private void throwPhoneOccupied(String phone, String reason) {
        log.warn("[bindOrTransfer] 并发冲突({})：手机号已被抢占 phone={}", reason, maskPhone(phone));
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
    private void writeTransferLog(Long fromUserId, Long toUserId, String phone, LocalDateTime now) {
        phoneTransferLogMapper.insert(new PhoneTransferLog()
                .setTransferNo(IdUtil.fastSimpleUUID())
                .setPhone(phone)
                .setFromUserId(fromUserId)
                .setToUserId(toUserId)
                .setScene(TRANSFER_SCENE_BIND)
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