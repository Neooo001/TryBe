package com.trybe.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.domain.PageDTO;
import com.trybe.common.domain.PageQuery;
import com.trybe.common.exception.BadRequestException;
import com.trybe.common.exception.BizIllegalException;
import com.trybe.common.exception.ForbiddenException;
import com.trybe.common.service.OssService;
import com.trybe.common.utils.BeanUtils;
import com.trybe.common.utils.FileTypeUtils;
import com.trybe.user.domain.dto.CreatorApplyReq;
import com.trybe.user.domain.dto.LicensePresignReq;
import com.trybe.user.domain.dto.ReviewApplicationReq;
import com.trybe.user.domain.po.CreatorApplication;
import com.trybe.user.domain.po.User;
import com.trybe.user.domain.vo.ApplicationVO;
import com.trybe.user.domain.vo.PresignVO;
import com.trybe.user.mapper.CreatorApplicationMapper;
import com.trybe.user.mapper.UserMapper;
import com.trybe.user.service.ICreatorApplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 创作者入驻申请服务实现
 *
 * <p>状态机：PENDING（待审）→ APPROVED（通过，同时改 user.role=CREATOR）
 * / REJECTED（驳回，记录原因后可原记录重提回 PENDING）。
 *
 * <p>角色：缓存由本服务维护，key = {@code creator:role:{userId}}，TTL 30min；
 * 入驻审核通过后主动删除该 key，creator-service 下次请求回源 DB。
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreatorApplyServiceImpl extends ServiceImpl<CreatorApplicationMapper, CreatorApplication>
        implements ICreatorApplyService {

    /** 角色 Redis 缓存 key 前缀（与 creator-service RoleGuard 保持一致） */
    private static final String REDIS_ROLE_KEY_PREFIX = "creator:role:";
    private static final long ROLE_CACHE_TTL_MINUTES = 30L;

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";

    private static final String ROLE_CREATOR = "CREATOR";
    private static final String ROLE_ADMIN = "ADMIN";

    private static final long PRESIGN_EXPIRE_SECONDS = 3600L;
    private static final String LICENSE_PATH_TEMPLATE = "user/%d/creator/license.%s";

    private final UserMapper userMapper;
    private final OssService ossService;
    private final StringRedisTemplate stringRedisTemplate;

    // ==================== U2 资质图预签名 ====================

    /**
     * 生成入驻资质图预签名上传信息（含 OSS 外部交互，不加 @Transactional）
     */
    @Override
    public PresignVO presignLicense(Long userId, LicensePresignReq req) {
        String ext = FileTypeUtils.normalizeExtension(req.getExt());
        if (!FileTypeUtils.isSupportedImage(ext)) {
            throw new BadRequestException("不支持的图片格式，仅支持 " + FileTypeUtils.supportedImageExtensions());
        }
        String objectKey = String.format(LICENSE_PATH_TEMPLATE, userId, ext);
        String uploadUrl = ossService.generatePresignedPutUrl(objectKey, PRESIGN_EXPIRE_SECONDS,
                FileTypeUtils.contentTypeOf(ext));
        // 日志脱敏：只打 objectKey 与有效期，绝不打印预签名 URL
        log.info("[creatorApply] 资质图预签名生成 userId={} objectKey={} expire={}s", userId, objectKey, PRESIGN_EXPIRE_SECONDS);
        return new PresignVO(objectKey, uploadUrl, PRESIGN_EXPIRE_SECONDS);
    }

    // ==================== U3 提交入驻申请 ====================

    /**
     * 提交入驻申请：每用户仅一条（uk_user），REJECTED 后可重提；PENDING 重复提交拦截
     */
    @Override
    @Transactional
    public ApplicationVO apply(Long userId, CreatorApplyReq req) {
        CreatorApplication exist = lambdaQuery().eq(CreatorApplication::getUserId, userId).one();
        if (exist != null) {
            if (STATUS_PENDING.equals(exist.getStatus())) {
                throw new BizIllegalException(ErrorCodeEnum.DUPLICATE_SUBMIT, "入驻申请审核中，请勿重复提交");
            }
            if (STATUS_APPROVED.equals(exist.getStatus())) {
                throw new BizIllegalException(ErrorCodeEnum.BIZ_ERROR, "已通过入驻审核，无需再次申请");
            }
            // REJECTED → 原记录重提，重置结论字段
            exist.setShopName(req.getShopName());
            exist.setShopIntro(req.getShopIntro());
            exist.setContactPhone(req.getContactPhone());
            exist.setLicenseImageKey(req.getLicenseImageKey());
            exist.setCategoryScope(req.getCategoryScope());
            exist.setStatus(STATUS_PENDING);
            exist.setRejectReason(null);
            exist.setReviewerId(null);
            exist.setReviewedAt(null);
            updateById(exist);
            log.info("[creatorApply] 驳回后重提入驻申请 userId={} applicationId={}", userId, exist.getId());
            return toVO(exist, null);
        }

        CreatorApplication application = new CreatorApplication()
                .setUserId(userId)
                .setShopName(req.getShopName())
                .setShopIntro(req.getShopIntro())
                .setContactPhone(req.getContactPhone())
                .setLicenseImageKey(req.getLicenseImageKey())
                .setCategoryScope(req.getCategoryScope())
                .setStatus(STATUS_PENDING);
        save(application);
        log.info("[creatorApply] 提交入驻申请 userId={} applicationId={}", userId, application.getId());
        return toVO(application, null);
    }

    // ==================== U4 查我的申请 ====================

    @Override
    public ApplicationVO getMyApplication(Long userId) {
        CreatorApplication application = lambdaQuery().eq(CreatorApplication::getUserId, userId).one();
        if (application == null) {
            return null;
        }
        return toVO(application, nicknameOf(userId));
    }

    // ==================== U5 运营审核列表 ====================

    @Override
    public PageDTO<ApplicationVO> pageApplications(Long adminId, String status, PageQuery query) {
        requireAdmin(adminId);

        LambdaQueryWrapper<CreatorApplication> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(CreatorApplication::getStatus, status.trim());
        }
        Page<CreatorApplication> page = page(query.toMpPageDefaultSortByCreateTimeDesc(), wrapper);
        if (page.getRecords().isEmpty()) {
            return PageDTO.empty(page);
        }

        // 批量读取申请人昵称（同库 user 表，无需跨服务）
        List<Long> userIds = page.getRecords().stream()
                .map(CreatorApplication::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> nicknameMap = nicknameMapOf(userIds);

        List<ApplicationVO> voList = page.getRecords().stream()
                .map(a -> toVO(a, nicknameMap.get(a.getUserId())))
                .collect(Collectors.toList());
        return PageDTO.of(page, voList);
    }

    // ==================== U6 入驻审核 ====================

    /**
     * 入驻审核：纯 DB 写（含 user.role 更新），允许事务；Redis 缓存删除放在 DB 写之后，
     * 失败也不影响已提交的事务结果（creator-service 最坏 30min TTL 回源兜底）。
     */
    @Override
    @Transactional
    public void reviewApplication(Long adminId, Long id, ReviewApplicationReq req) {
        requireAdmin(adminId);

        CreatorApplication application = getById(id);
        if (application == null) {
            throw new BizIllegalException(ErrorCodeEnum.DATA_NOT_FOUND, "入驻申请不存在");
        }
        if (!STATUS_PENDING.equals(application.getStatus())) {
            throw new BizIllegalException(ErrorCodeEnum.STATUS_ERROR, "该申请已审核，无法重复处理");
        }

        boolean approve = Boolean.TRUE.equals(req.getApprove());
        LocalDateTime now = LocalDateTime.now();
        application.setReviewerId(adminId);
        application.setReviewedAt(now);
        application.setStatus(approve ? STATUS_APPROVED : STATUS_REJECTED);
        application.setRejectReason(approve ? null : StrUtil.trimToNull(req.getReason()));
        if (!approve && application.getRejectReason() == null) {
            throw new BadRequestException("驳回时必须填写驳回原因");
        }
        updateById(application);

        if (approve) {
            // 审核通过 = 升级为创作者：仅本服务写 user 表（creator-service 只读）
            boolean upgraded = lambdaUpdateUserRole(application.getUserId(), ROLE_CREATOR);
            if (!upgraded) {
                // 用户不存在：回滚本次状态变更
                throw new BizIllegalException(ErrorCodeEnum.DATA_NOT_FOUND, "申请用户不存在，审核失败");
            }
            deleteRoleCache(application.getUserId());
            log.info("[creatorApply] 入驻审核通过 userId={} applicationId={} reviewer={}",
                    application.getUserId(), id, adminId);
        } else {
            log.info("[creatorApply] 入驻审核驳回 userId={} applicationId={} reviewer={}",
                    application.getUserId(), id, adminId);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 运营身份守卫：Redis 缓存优先，miss 回源 user 表，非 ADMIN 抛 401
     */
    private void requireAdmin(Long userId) {
        String role = queryRole(userId);
        if (!ROLE_ADMIN.equals(role)) {
            throw new ForbiddenException(ErrorCodeEnum.FORBIDDEN, "仅运营账号可执行该操作");
        }
    }

    /**
     * 查询角色：Redis → user 表，并回填缓存
     */
    private String queryRole(Long userId) {
        String cacheKey = REDIS_ROLE_KEY_PREFIX + userId;
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StrUtil.isNotBlank(cached)) {
                return cached;
            }
        } catch (Exception e) {
            log.warn("[creatorApply] 读取角色缓存失败，回源DB userId={}", userId, e);
        }

        User user = userMapper.selectById(userId);
        String role = user == null ? null : StrUtil.trimToNull(user.getRole());
        if (StrUtil.isNotBlank(role)) {
            try {
                stringRedisTemplate.opsForValue().set(cacheKey, role, ROLE_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("[creatorApply] 回填角色缓存失败 userId={}", userId, e);
            }
        }
        return role;
    }

    /**
     * 填写之后的缓存删除：role 变更后必须清 creator-service 侧缓存
     */
    private void deleteRoleCache(Long userId) {
        try {
            stringRedisTemplate.delete(REDIS_ROLE_KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("[creatorApply] 删除角色缓存失败 userId={}", userId, e);
        }
    }

    /**
     * 更新用户角色为指定值
     */
    private boolean lambdaUpdateUserRole(Long userId, String role) {
        User update = new User();
        update.setId(userId);
        update.setRole(role);
        return userMapper.updateById(update) > 0;
    }

    private ApplicationVO toVO(CreatorApplication application, String nickname) {
        ApplicationVO vo = BeanUtils.copyBean(application, ApplicationVO.class);
        vo.setUserId(application.getUserId());
        vo.setNickname(nickname);
        if (StrUtil.isNotBlank(application.getLicenseImageKey())) {
            vo.setLicenseUrl(ossService.generatePresignedUrl(application.getLicenseImageKey()));
        }
        return vo;
    }

    private String nicknameOf(Long userId) {
        User user = userMapper.selectById(userId);
        return user == null ? null : user.getNickname();
    }

    private Map<Long, String> nicknameMapOf(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .select(User::getId, User::getNickname)
                        .in(User::getId, userIds));
        return users.stream().collect(Collectors.toMap(User::getId, u -> u.getNickname() == null ? "" : u.getNickname(),
                (a, b) -> a));
    }
}
