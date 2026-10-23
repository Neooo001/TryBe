package com.trybe.creator.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BadRequestException;
import com.trybe.common.exception.BizIllegalException;
import com.trybe.common.service.OssService;
import com.trybe.creator.domain.dto.CreatorProfileUpdateReq;
import com.trybe.creator.domain.enums.CreatorField;
import com.trybe.creator.domain.enums.HomepageStatus;
import com.trybe.creator.domain.po.CreatorProfile;
import com.trybe.creator.domain.po.AccountBrief;
import com.trybe.creator.domain.vo.CreatorProfileVO;
import com.trybe.creator.mapper.CreatorProfileMapper;
import com.trybe.creator.mapper.AccountBriefMapper;
import com.trybe.creator.service.ICreatorProfileService;
import com.trybe.creator.util.CreatorOssPathBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * 创作者个人中心服务实现
 *
 * <p>事务边界：本类为<b>纯 DB 流程</b>（预签名 URL 生成是本地签名计算，无外部 IO），
 * 更新方法按项目事务约定加 @Transactional；OSS 外部交互全部收敛在 UploadService
 * 的 P1/P2 直传流程中（红线①），本类不直接触碰 OSS 读写。
 *
 * <p>存储约定：头像与主页封面图存放在<b>第二 bucket</b>
 * （{@code aliyun.oss.bucket-name2}），与物品图/模特图物理隔离；
 * 生成访问 URL 时必须显式传入该 bucket，与 UploadService 的写入侧保持同源。
 *
 * <p>安全约定：
 * <ul>
 *   <li>creatorId 一律由 Controller 从 UserContext 取入参，资料行按 uk_creator 唯一键定位，
 *       不存在「读他人资料」的入参面</li>
 *   <li>账号资料经 app_user 只读投影获取（不映射 password_hash），手机号脱敏后才出服务层</li>
 *   <li>头像/封面绑定强校验归档位（isProfileAvatar / isProfileCover），
 *       杜绝绑定他人对象或物品图等非个人中心用途对象</li>
 * </ul>
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreatorProfileServiceImpl extends ServiceImpl<CreatorProfileMapper, CreatorProfile>
        implements ICreatorProfileService {

    private static final String PHONE_MASK_REGEX = "(\\d{3})\\d{4}(\\d{4})";
    private static final String PHONE_MASK_REPLACEMENT = "$1****$2";
    /** 懒创建默认行时的兜底昵称（app_user.nickname 非空，正常不会走到） */
    private static final String DEFAULT_NICKNAME = "创作者";

    private final AccountBriefMapper accountBriefMapper;
    private final OssService ossService;

    // ==================== 获取个人中心资料 ====================

    @Override
    public CreatorProfileVO getMyProfile(Long creatorId) {
        AccountBrief account = requireAccount(creatorId);
        CreatorProfile profile = getOrCreateProfile(creatorId, account.getNickname());
        return buildVO(profile, account);
    }

    // ==================== 更新个人中心资料（白名单字段） ====================

    @Override
    @Transactional
    public CreatorProfileVO updateMyProfile(Long creatorId, CreatorProfileUpdateReq req) {
        AccountBrief account = requireAccount(creatorId);
        CreatorProfile profile = getOrCreateProfile(creatorId, account.getNickname());

        // 昵称 / 头像 / 简介 由 user-service 的 PUT /web/users/profile 维护（存 app_user），
        // 本接口不再接受，DTO 提交即忽略。

        // —— 封面图：null=不修改；""=恢复默认；非空=强校验本人归档位 ——
        if (req.getCoverKey() != null) {
            profile.setCoverKey(validateProfileImageKey(creatorId, req.getCoverKey(),
                    CreatorOssPathBuilder::isProfileCover, "封面图"));
        }

        // —— 创作方向：null=不修改；[]=清空；非空=逐个严格解析 + 去重 + 上限校验 ——
        if (req.getFieldCodes() != null) {
            profile.setFieldCodes(validateAndJoinFieldCodes(req.getFieldCodes()));
        }

        // homepageStatus / creatorId / 账号字段不在白名单：DTO 无绑定，提交即忽略
        updateById(profile);
        log.info("[profile] 主页资料已更新 creatorId={} coverBound={} fieldCodes={}",
                creatorId, profile.getCoverKey() != null, profile.getFieldCodes());
        return buildVO(profile, account);
    }

    // ==================== 私有：档案定位与懒创建 ====================

    /**
     * app_user 只读投影必须存在：账号被删除/合并后不应再产生新的创作数据，
     * 行不存在说明账号已注销/归档，统一走 DATA_NOT_FOUND
     */
    private AccountBrief requireAccount(Long creatorId) {
        AccountBrief account = accountBriefMapper.selectBriefById(creatorId);
        if (account == null) {
            throw new BizIllegalException(ErrorCodeEnum.DATA_NOT_FOUND, "账号不存在或已注销");
        }
        return account;
    }

    /**
     * 档案行懒创建：首访兜底插入默认行（昵称取注册昵称）；
     * 并发首访由 uk_creator 唯一键兜底，冲突后重查一次
     */
    private CreatorProfile getOrCreateProfile(Long creatorId, String defaultNickname) {
        CreatorProfile profile = getByCreatorId(creatorId);
        if (profile != null) {
            return profile;
        }
        try {
            profile = new CreatorProfile()
                    .setCreatorId(creatorId)
                    .setNickname(StrUtil.blankToDefault(defaultNickname, DEFAULT_NICKNAME))
                    .setHomepageStatus(HomepageStatus.ENABLED.getCode());
            save(profile);
            log.info("[profile] 懒创建默认档案行 creatorId={}", creatorId);
            return profile;
        } catch (DuplicateKeyException e) {
            // 并发首访：另一事务刚插入成功，重查一次（MySQL 唯一键冲突不会中断事务，可继续）
            CreatorProfile existed = getByCreatorId(creatorId);
            if (existed == null) {
                // 理论上不可达（唯一键冲突即说明行已存在），兜底抛业务异常而非 NPE
                throw new BizIllegalException(ErrorCodeEnum.DB_ERROR, "个人资料初始化失败，请重试");
            }
            return existed;
        }
    }

    private CreatorProfile getByCreatorId(Long creatorId) {
        return getOne(new LambdaQueryWrapper<CreatorProfile>()
                .eq(CreatorProfile::getCreatorId, creatorId));
    }

    // ==================== 私有：字段校验 ====================

    /**
     * 个人中心图片 key 校验：空串=恢复默认（返回 null）；
     * 非空必须精确命中本人对应归档位（防越权绑定他人对象 / 物品图等非本用途对象）
     */
    private String validateProfileImageKey(Long creatorId, String rawKey,
                                           BiPredicate<Long, String> matcher,
                                           String label) {
        String key = StrUtil.trimToNull(rawKey);
        if (key == null) {
            return null;
        }
        if (!matcher.test(creatorId, key)) {
            throw new BadRequestException(
                    label + "必须通过个人中心上传接口获取，不可绑定其他图片");
        }
        return key;
    }

    /**
     * 创作方向校验：逐个 strictOf（非法编码直接 100）→ LinkedHashSet 去重 → 上限 5 个 → 逗号拼接
     */
    private String validateAndJoinFieldCodes(List<String> codes) {
        if (codes.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String code : codes) {
            normalized.add(CreatorField.strictOf(code).getCode());
        }
        if (normalized.size() > CreatorField.MAX_SELECTED) {
            throw new BadRequestException("创作方向最多选择" + CreatorField.MAX_SELECTED + "个");
        }
        return String.join(",", normalized);
    }

    // ==================== 私有：VO 组装 ====================

    private CreatorProfileVO buildVO(CreatorProfile profile, AccountBrief accountBrief) {
        CreatorProfileVO vo = new CreatorProfileVO();
        vo.setCreatorId(profile.getCreatorId());
        // 昵称 / 头像 / 简介 一律取 app_user（唯一资料源），creator_profile 同名列已冻结
        vo.setNickname(accountBrief.getNickname());
        vo.setAvatarKey(accountBrief.getAvatar());
        vo.setAvatarUrl(accessUrlOrNull(accountBrief.getAvatar(), versionOf(accountBrief.getUpdateTime())));
        vo.setBio(accountBrief.getBio());
        // 封面图仍是创作者主页专属，存 creator_profile
        Long version = versionOf(profile.getUpdateTime());
        vo.setCoverKey(profile.getCoverKey());
        vo.setCoverUrl(accessUrlOrNull(profile.getCoverKey(), version));
        vo.setFieldCodes(splitFieldCodes(profile.getFieldCodes()));
        vo.setHomepageStatus(profile.getHomepageStatus());

        CreatorProfileVO.AccountVO account = new CreatorProfileVO.AccountVO();
        account.setPhoneMasked(maskPhone(accountBrief.getPhone()));
        account.setNickname(accountBrief.getNickname());
        account.setAvatarKey(accountBrief.getAvatar());
        account.setAvatarUrl(accessUrlOrNull(accountBrief.getAvatar(), versionOf(accountBrief.getUpdateTime())));
        account.setBio(accountBrief.getBio());
        account.setCreateTime(accountBrief.getCreateTime());
        account.setLastLoginTime(accountBrief.getLastLoginTime());
        vo.setAccount(account);

        List<CreatorProfileVO.FieldOptionVO> options = new ArrayList<>();
        for (CreatorField field : CreatorField.all()) {
            options.add(new CreatorProfileVO.FieldOptionVO(field.getCode(), field.getDesc()));
        }
        vo.setFieldOptions(options);
        vo.setFieldMax(CreatorField.MAX_SELECTED);
        return vo;
    }

    /**
     * key 为空返回 null（前端用默认占位图）；URL 每次现算，不落库
     *
     * <p>头像 / 主页封面图存放于第二 bucket（aliyun.oss.bucket-name2），
     * 取 URL 时 bucket 必须与上传时一致，否则会拿默认桶去签第二桶的对象 → 404/403。
     * 该桶为<b>公共读</b>时下发永久公开 URL（无签名、不过期），否则回落预签名 URL，
     * 由 OssService.accessUrl 按配置自动判定，本类不感知权限细节。
     *
     * <p>带版本号的目的见 {@link com.trybe.common.service.OssService#accessUrl(String, String, Long)}：
     * 换图是「同 key 覆盖」，URL 恒定会被浏览器缓存住，必须靠变化的版本号强制刷新。
     */
    private String accessUrlOrNull(String objectKey, Long version) {
        return ossService.accessUrl(ossService.secondBucketName(), objectKey, version);
    }

    /**
     * 记录更新时间 → 版本号（毫秒时间戳）
     *
     * <p>用它而非当前时间：内容没变时版本号恒定，缓存照常命中；
     * 一旦换图 update_time 就会变（DB 的 ON UPDATE CURRENT_TIMESTAMP），版本号跟着变。
     */
    private Long versionOf(LocalDateTime updateTime) {
        return updateTime == null ? null : updateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private List<String> splitFieldCodes(String joined) {
        if (StrUtil.isBlank(joined)) {
            return new ArrayList<>();
        }
        return StrUtil.splitTrim(joined, ',');
    }

    /** 手机号脱敏：138****8000（与 WebAuthController 日志脱敏同规则） */
    private String maskPhone(String phone) {
        if (StrUtil.isBlank(phone)) {
            return null;
        }
        return phone.replaceAll(PHONE_MASK_REGEX, PHONE_MASK_REPLACEMENT);
    }
}
