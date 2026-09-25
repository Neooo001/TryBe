package com.trybe.user.service.impl;

import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BizIllegalException;
import com.trybe.user.domain.dto.WebRegisterDTO;
import com.trybe.user.domain.po.WebUser;
import com.trybe.user.domain.po.WebUserArchive;
import com.trybe.user.enums.UserStatus;
import com.trybe.user.mapper.WebUserArchiveMapper;
import com.trybe.user.mapper.WebUserMapper;
import com.trybe.user.service.WebUserTxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Web 域用户表事务实现（纯 DB，无外部交互）
 *
 * <p>关键分支在 {@link #registerInTx(WebRegisterDTO)}：
 * <pre>
 *   锁住手机号行（FOR UPDATE）
 *   ├─ 未被占用 → 直接建号
 *   ├─ 占用且活跃（最近登录距今 &lt; 180 天）→ PHONE_OCCUPIED 拒绝
 *   └─ 占用且沉睡（≥ 180 天未登录，疑似运营商二次放号）→ 整行归档后放行建号
 * </pre>
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebUserTxServiceImpl implements WebUserTxService {

    /** 沉睡阈值（天）：超过该时长未登录视为疑似二手号，归档释放后允许新注册 */
    private static final long DORMANT_DAYS = 180L;

    /** 归档原因 */
    private static final String ARCHIVE_REASON = "SLEEP_RELEASE";

    private static final String DEFAULT_NICKNAME = "小Try";

    private static final String PHONE_MASK_REGEX = "(\\d{3})\\d{4}(\\d{4})";
    private static final String PHONE_MASK_REPLACEMENT = "$1****$2";

    private final WebUserMapper webUserMapper;
    private final WebUserArchiveMapper webUserArchiveMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WebUser registerInTx(WebRegisterDTO dto) {
        String phone = dto.getPhone().trim();
        LocalDateTime now = LocalDateTime.now();

        // 排他锁：串行化同一手机号的并发注册（phone 有唯一索引，命中锁行、未命中为间隙锁）
        WebUser existing = webUserMapper.selectByPhoneForUpdate(phone);
        if (existing != null) {
            LocalDateTime lastActive = existing.getLastLoginTime() != null
                    ? existing.getLastLoginTime() : existing.getCreateTime();
            if (lastActive != null && lastActive.isAfter(now.minusDays(DORMANT_DAYS))) {
                log.info("[webAuth] 注册被拒：手机号活跃占用 userId={} phone={}",
                        existing.getId(), maskPhone(phone));
                throw new BizIllegalException(ErrorCodeEnum.PHONE_OCCUPIED, "该手机号已被注册");
            }
            // 沉睡账号：整行搬迁到归档表后释放手机号
            webUserArchiveMapper.insert(WebUserArchive.of(existing, ARCHIVE_REASON));
            webUserMapper.deleteById(existing.getId());
            log.warn("[webAuth] 沉睡账号已归档并释放手机号 userId={} phone={} lastActive={}",
                    existing.getId(), maskPhone(phone), lastActive);
        }

        WebUser user = new WebUser()
                .setPhone(phone)
                .setPasswordHash(passwordEncoder.encode(dto.getPassword()))
                .setNickname(org.springframework.util.StringUtils.hasText(dto.getNickname())
                        ? dto.getNickname().trim() : DEFAULT_NICKNAME)
                .setStatus(UserStatus.NORMAL)
                .setLastLoginTime(now);
        try {
            webUserMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发窗口：另一事务抢先插入同一手机号
            throw new BizIllegalException(ErrorCodeEnum.PHONE_OCCUPIED, "该手机号已被注册");
        }

        log.info("[webAuth] 注册成功 userId={} phone={}", user.getId(), maskPhone(phone));
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePasswordHashInTx(Long userId, String passwordHash) {
        webUserMapper.updateById(new WebUser().setId(userId).setPasswordHash(passwordHash));
    }

    /** 手机号脱敏：138****8000 */
    private String maskPhone(String phone) {
        if (phone == null) {
            return "***";
        }
        return phone.replaceAll(PHONE_MASK_REGEX, PHONE_MASK_REPLACEMENT);
    }
}
