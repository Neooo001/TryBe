package com.trybe.creator.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.domain.PageDTO;
import com.trybe.common.exception.BadRequestException;
import com.trybe.common.exception.BizIllegalException;
import com.trybe.common.service.OssService;
import com.trybe.common.utils.BeanUtils;
import com.trybe.common.utils.CollUtils;
import com.trybe.creator.config.ItemNoGenerator;
import com.trybe.creator.domain.dto.ItemPageQuery;
import com.trybe.creator.domain.dto.ItemSaveReq;
import com.trybe.creator.domain.dto.ReviewReq;
import com.trybe.creator.domain.dto.UserBrief;
import com.trybe.creator.domain.enums.AuditAction;
import com.trybe.creator.domain.enums.CreatorItemStatus;
import com.trybe.creator.domain.enums.GarmentCategoryL1;
import com.trybe.creator.domain.enums.TryonMode;
import com.trybe.creator.domain.po.CreatorItem;
import com.trybe.creator.domain.vo.ItemDetailVO;
import com.trybe.creator.domain.vo.ItemListVO;
import com.trybe.creator.mapper.CreatorItemMapper;
import com.trybe.creator.mapper.UserReadMapper;
import com.trybe.creator.service.IAuditRecordService;
import com.trybe.creator.service.ICreatorItemService;
import com.trybe.creator.service.IModelImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 创作者物品服务实现（含运营侧审核复用）
 *
 * <p>事务边界：状态流转 + 流水写入是纯 DB 操作，加 @Transactional；
 * 涉及 OSS/人脸等外部调用的流程在 ModelImageServiceImpl / UploadService 中，均无事务（红线①）。
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreatorItemServiceImpl extends ServiceImpl<CreatorItemMapper, CreatorItem>
        implements ICreatorItemService {

    private static final String AUDIT_OBJECT_TYPE = "CREATOR_ITEM";

    private final ItemNoGenerator itemNoGenerator;
    private final OssService ossService;
    private final IModelImageService modelImageService;
    private final IAuditRecordService auditRecordService;
    private final UserReadMapper userReadMapper;

    // ==================== I1 我的物品列表 ====================

    @Override
    public PageDTO<ItemListVO> pageMyItems(Long creatorId, ItemPageQuery query) {
        LambdaQueryWrapper<CreatorItem> wrapper = buildQueryWrapper(creatorId, query);
        return toPageDTO(page(query.toMpPageDefaultSortByCreateTimeDesc(), wrapper), Collections.emptyMap());
    }

    // ==================== I2 我的物品详情 ====================

    @Override
    public ItemDetailVO getMyItem(Long creatorId, Long id) {
        CreatorItem item = getOwnedItem(creatorId, id);
        return toDetailVO(item, null);
    }

    // ==================== I3 新建（DRAFT） ====================

    @Override
    @Transactional
    public ItemDetailVO createItem(Long creatorId, ItemSaveReq req) {
        if (StrUtil.isBlank(req.getTitle())) {
            throw new BadRequestException("物品标题不能为空");
        }
        GarmentCategoryL1 category = GarmentCategoryL1.strictOf(req.getCategoryCode());

        CreatorItem item = new CreatorItem()
                .setItemNo(itemNoGenerator.nextItemNo())
                .setCreatorId(creatorId)
                .setCategoryCode(category.getCode())
                .setTitle(StrUtil.trim(req.getTitle()))
                .setDescription(StrUtil.trimToNull(req.getDescription()))
                .setPrice(req.getPrice())
                .setStatus(CreatorItemStatus.DRAFT.getCode())
                .setTryonMode(resolveTryonMode(req.getTryonMode()).getCode())
                .setColorHex(StrUtil.trimToNull(req.getColorHex()))
                .setMaterial(StrUtil.trimToNull(req.getMaterial()))
                .setPatternTags(toJson(req.getPatternTags()))
                .setImageKey(StrUtil.trimToNull(req.getImageKey()))
                .setCutoutPngKey(StrUtil.trimToNull(req.getCutoutPngKey()))
                .setReferenceKeys(toJson(req.getReferenceKeys()))
                .setRealWidthMm(req.getRealWidthMm())
                .setRealHeightMm(req.getRealHeightMm())
                .setPxPerMm(req.getPxPerMm())
                .setAnchorJson(req.getAnchorJson() == null ? null : JSONUtil.toJsonStr(req.getAnchorJson()));
        save(item);
        log.info("[item] 物品已创建 id={} itemNo={} creatorId={}", item.getId(), item.getItemNo(), creatorId);
        return toDetailVO(item, null);
    }

    // ==================== I4 编辑（null = 不修改） ====================

    @Override
    @Transactional
    public ItemDetailVO updateItem(Long creatorId, Long id, ItemSaveReq req) {
        CreatorItem item = getOwnedItem(creatorId, id);
        CreatorItemStatus status = CreatorItemStatus.of(item.getStatus());
        if (status == null || !status.editable()) {
            throw new BizIllegalException(ErrorCodeEnum.STATUS_ERROR, "当前状态不可编辑，请先下架");
        }

        LambdaUpdateWrapper<CreatorItem> updateWrapper = new LambdaUpdateWrapper<CreatorItem>()
                .eq(CreatorItem::getId, id)
                .eq(CreatorItem::getCreatorId, creatorId);
        boolean changed = false;

        if (StrUtil.isNotBlank(req.getTitle())) {
            updateWrapper.set(CreatorItem::getTitle, StrUtil.trim(req.getTitle()));
            changed = true;
        }
        if (StrUtil.isNotBlank(req.getCategoryCode())) {
            updateWrapper.set(CreatorItem::getCategoryCode, GarmentCategoryL1.strictOf(req.getCategoryCode()).getCode());
            changed = true;
        }
        if (StrUtil.isNotBlank(req.getDescription())) {
            updateWrapper.set(CreatorItem::getDescription, StrUtil.trim(req.getDescription()));
            changed = true;
        }
        if (req.getPrice() != null) {
            updateWrapper.set(CreatorItem::getPrice, req.getPrice());
            changed = true;
        }
        if (StrUtil.isNotBlank(req.getTryonMode())) {
            updateWrapper.set(CreatorItem::getTryonMode, resolveTryonMode(req.getTryonMode()).getCode());
            changed = true;
        }
        if (StrUtil.isNotBlank(req.getColorHex())) {
            updateWrapper.set(CreatorItem::getColorHex, StrUtil.trim(req.getColorHex()));
            changed = true;
        }
        if (StrUtil.isNotBlank(req.getMaterial())) {
            updateWrapper.set(CreatorItem::getMaterial, StrUtil.trim(req.getMaterial()));
            changed = true;
        }
        if (req.getPatternTags() != null) {
            updateWrapper.set(CreatorItem::getPatternTags, toJson(req.getPatternTags()));
            changed = true;
        }
        if (StrUtil.isNotBlank(req.getImageKey())) {
            updateWrapper.set(CreatorItem::getImageKey, StrUtil.trim(req.getImageKey()));
            changed = true;
        }
        if (StrUtil.isNotBlank(req.getCutoutPngKey())) {
            updateWrapper.set(CreatorItem::getCutoutPngKey, StrUtil.trim(req.getCutoutPngKey()));
            changed = true;
        }
        if (req.getReferenceKeys() != null) {
            updateWrapper.set(CreatorItem::getReferenceKeys, toJson(req.getReferenceKeys()));
            changed = true;
        }
        if (req.getRealWidthMm() != null) {
            updateWrapper.set(CreatorItem::getRealWidthMm, req.getRealWidthMm());
            changed = true;
        }
        if (req.getRealHeightMm() != null) {
            updateWrapper.set(CreatorItem::getRealHeightMm, req.getRealHeightMm());
            changed = true;
        }
        if (req.getPxPerMm() != null) {
            updateWrapper.set(CreatorItem::getPxPerMm, req.getPxPerMm());
            changed = true;
        }
        if (req.getAnchorJson() != null) {
            updateWrapper.set(CreatorItem::getAnchorJson, JSONUtil.toJsonStr(req.getAnchorJson()));
            changed = true;
        }

        if (changed) {
            update(updateWrapper);
        }
        log.info("[item] 物品已更新 id={} creatorId={} changed={}", id, creatorId, changed);
        return toDetailVO(getById(id), null);
    }

    // ==================== I5 删除 ====================

    @Override
    @Transactional
    public void deleteItem(Long creatorId, Long id) {
        CreatorItem item = getOwnedItem(creatorId, id);
        CreatorItemStatus status = CreatorItemStatus.of(item.getStatus());
        if (status == null || !status.deletable()) {
            throw new BizIllegalException(ErrorCodeEnum.STATUS_ERROR, "当前状态不可删除，请先下架");
        }
        removeById(id);
        log.info("[item] 物品已删除 id={} creatorId={}", id, creatorId);
    }

    // ==================== I6 提交审核 ====================

    @Override
    @Transactional
    public void submit(Long creatorId, Long id) {
        CreatorItem item = getOwnedItem(creatorId, id);
        CreatorItemStatus status = CreatorItemStatus.of(item.getStatus());
        if (status == null || !status.submittable()) {
            throw new BizIllegalException(ErrorCodeEnum.STATUS_ERROR, "当前状态不可提交审核");
        }
        validateSubmittable(item);

        update(new LambdaUpdateWrapper<CreatorItem>()
                .eq(CreatorItem::getId, id)
                .eq(CreatorItem::getCreatorId, creatorId)
                .set(CreatorItem::getStatus, CreatorItemStatus.PENDING.getCode())
                .set(CreatorItem::getRejectReason, null));
        auditRecordService.record(AUDIT_OBJECT_TYPE, id, AuditAction.SUBMIT, creatorId, null);
        log.info("[item] 物品已提交审核 id={} creatorId={}", id, creatorId);
    }

    // ==================== I7 / I8 上下架 ====================

    @Override
    @Transactional
    public void onShelf(Long creatorId, Long id) {
        CreatorItem item = getOwnedItem(creatorId, id);
        CreatorItemStatus status = CreatorItemStatus.of(item.getStatus());
        if (status == null || !status.canOnShelf()) {
            throw new BizIllegalException(ErrorCodeEnum.STATUS_ERROR, "仅已通过或已下架的物品可上架");
        }
        auditRecordService.record(AUDIT_OBJECT_TYPE, id, AuditAction.ON_SHELF, creatorId, null);
        updateStatus(id, CreatorItemStatus.ON_SHELF);
        log.info("[item] 物品已上架 id={} creatorId={}", id, creatorId);
    }

    @Override
    @Transactional
    public void offShelf(Long creatorId, Long id) {
        CreatorItem item = getOwnedItem(creatorId, id);
        CreatorItemStatus status = CreatorItemStatus.of(item.getStatus());
        if (status == null || !status.canOffShelf()) {
            throw new BizIllegalException(ErrorCodeEnum.STATUS_ERROR, "仅已上架的物品可下架");
        }
        auditRecordService.record(AUDIT_OBJECT_TYPE, id, AuditAction.OFF_SHELF, creatorId, null);
        updateStatus(id, CreatorItemStatus.OFF_SHELF);
        log.info("[item] 物品已下架 id={} creatorId={}", id, creatorId);
    }

    // ==================== R1 运营待审队列 ====================

    @Override
    public PageDTO<ItemListVO> pagePendingItems(ItemPageQuery query) {
        LambdaQueryWrapper<CreatorItem> wrapper = buildQueryWrapper(null, query)
                .eq(CreatorItem::getStatus, CreatorItemStatus.PENDING.getCode());
        Page<CreatorItem> page = page(query.toMpPageDefaultSortByCreateTimeDesc(), wrapper);
        if (CollUtils.isEmpty(page.getRecords())) {
            return PageDTO.empty(page);
        }
        List<Long> creatorIds = page.getRecords().stream()
                .map(CreatorItem::getCreatorId)
                .distinct()
                .collect(Collectors.toList());
        return toPageDTO(page, nicknameMapOf(creatorIds));
    }

    // ==================== R2 运营审核详情 ====================

    @Override
    public ItemDetailVO getAdminItem(Long id) {
        CreatorItem item = getById(id);
        if (item == null) {
            throw new BizIllegalException(ErrorCodeEnum.DATA_NOT_FOUND, "物品不存在");
        }
        String nickname = safeReadNickname(item.getCreatorId());
        return toDetailVO(item, nickname);
    }

    // ==================== R3 物品审核 ====================

    @Override
    @Transactional
    public void review(Long adminId, Long id, ReviewReq req) {
        CreatorItem item = getById(id);
        if (item == null) {
            throw new BizIllegalException(ErrorCodeEnum.DATA_NOT_FOUND, "物品不存在");
        }
        CreatorItemStatus status = CreatorItemStatus.of(item.getStatus());
        if (status == null || !status.pending()) {
            throw new BizIllegalException(ErrorCodeEnum.STATUS_ERROR, "该物品不处于待审状态");
        }

        boolean approve = Boolean.TRUE.equals(req.getApprove());
        String reason = approve ? null : StrUtil.trimToNull(req.getReason());
        if (!approve && reason == null) {
            throw new BadRequestException("驳回时必须填写驳回原因");
        }

        update(new LambdaUpdateWrapper<CreatorItem>()
                .eq(CreatorItem::getId, id)
                .set(CreatorItem::getStatus, approve
                        ? CreatorItemStatus.APPROVED.getCode()
                        : CreatorItemStatus.REJECTED.getCode())
                .set(CreatorItem::getRejectReason, reason));
        auditRecordService.record(AUDIT_OBJECT_TYPE, id, approve ? AuditAction.APPROVE : AuditAction.REJECT,
                adminId, reason);
        log.info("[item] 物品审核完成 id={} approve={} adminId={}", id, approve, adminId);
    }

    // ==================== 私有方法 ====================

    /**
     * 构建通用查询条件：creatorId 为空表示运营视角（不限归属）
     */
    private LambdaQueryWrapper<CreatorItem> buildQueryWrapper(Long creatorId, ItemPageQuery query) {
        LambdaQueryWrapper<CreatorItem> wrapper = new LambdaQueryWrapper<>();
        if (creatorId != null) {
            wrapper.eq(CreatorItem::getCreatorId, creatorId);
        }
        if (query != null) {
            if (StrUtil.isNotBlank(query.getStatus())) {
                wrapper.eq(CreatorItem::getStatus, query.getStatus().trim());
            }
            if (StrUtil.isNotBlank(query.getCategoryCode())) {
                wrapper.eq(CreatorItem::getCategoryCode, query.getCategoryCode().trim());
            }
            if (StrUtil.isNotBlank(query.getKeyword())) {
                wrapper.like(CreatorItem::getTitle, query.getKeyword().trim());
            }
        }
        return wrapper;
    }

    /**
     * 获取本人物品，不存在或非本人一律按 402 处理（避免通过详情接口探测他人数据）
     */
    private CreatorItem getOwnedItem(Long creatorId, Long id) {
        CreatorItem item = getById(id);
        if (item == null || item.getCreatorId() == null || !item.getCreatorId().equals(creatorId)) {
            throw new BizIllegalException(ErrorCodeEnum.DATA_NOT_FOUND, "物品不存在");
        }
        return item;
    }

    /**
     * 提交审核前置校验：标题/分类/主图必填；试穿模式含模特时必须存在已授权的模特图
     */
    private void validateSubmittable(CreatorItem item) {
        if (StrUtil.isBlank(item.getTitle())) {
            throw new BadRequestException("物品标题不能为空");
        }
        if (StrUtil.isBlank(item.getCategoryCode())) {
            throw new BadRequestException("物品分类不能为空");
        }
        if (StrUtil.isBlank(item.getImageKey())) {
            throw new BadRequestException("请上传物品主图");
        }
        TryonMode mode = TryonMode.of(item.getTryonMode());
        if (mode.requireModelImage() && modelImageService.countAuthorized(item.getId()) <= 0) {
            throw new BadRequestException("请先上传模特图并确认肖像授权");
        }
    }

    private void updateStatus(Long id, CreatorItemStatus status) {
        update(new LambdaUpdateWrapper<CreatorItem>()
                .eq(CreatorItem::getId, id)
                .set(CreatorItem::getStatus, status.getCode()));
    }

    private PageDTO<ItemListVO> toPageDTO(Page<CreatorItem> page, Map<Long, String> nicknameMap) {
        if (CollUtils.isEmpty(page.getRecords())) {
            return PageDTO.empty(page);
        }
        List<ItemListVO> voList = page.getRecords().stream()
                .map(item -> {
                    ItemListVO vo = BeanUtils.copyBean(item, ItemListVO.class);
                    vo.setId(item.getId());
                    vo.setImageUrl(presignUrl(item.getImageKey()));
                    vo.setCreatorNickname(nicknameMap.getOrDefault(item.getCreatorId(), null));
                    return vo;
                })
                .collect(Collectors.toList());
        return PageDTO.of(page, voList);
    }

    private ItemDetailVO toDetailVO(CreatorItem item, String creatorNickname) {
        ItemDetailVO vo = BeanUtils.copyBean(item, ItemDetailVO.class);
        vo.setId(item.getId());
        vo.setImageUrl(presignUrl(item.getImageKey()));
        vo.setCutoutPngUrl(presignUrl(item.getCutoutPngKey()));
        vo.setReferenceUrls(referenceUrls(item.getReferenceKeys()));
        vo.setModelImages(modelImageService.listByItem(item.getId()));
        vo.setCreatorNickname(creatorNickname);
        return vo;
    }

    private List<String> referenceUrls(String referenceKeys) {
        if (StrUtil.isBlank(referenceKeys)) {
            return Collections.emptyList();
        }
        try {
            List<String> keys = JSONUtil.parseArray(referenceKeys).toList(String.class);
            return keys.stream().map(this::presignUrl).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[item] 参考图列表解析失败，降级为空列表 itemNo={}", referenceKeys == null ? null : "size=" + referenceKeys.length(), e);
            return Collections.emptyList();
        }
    }

    private String presignUrl(String objectKey) {
        if (StrUtil.isBlank(objectKey)) {
            return null;
        }
        try {
            return ossService.generatePresignedUrl(objectKey);
        } catch (Exception e) {
            // 图片 URL 生成失败不影响主体数据返回
            log.warn("[item] 生成预签名URL失败，降级为 null objectKey={}", objectKey, e);
            return null;
        }
    }

    private Map<Long, String> nicknameMapOf(List<Long> creatorIds) {
        if (creatorIds == null || creatorIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<UserBrief> briefs = userReadMapper.selectBriefsByIds(creatorIds);
            if (briefs == null) {
                return Collections.emptyMap();
            }
            return briefs.stream().collect(Collectors.toMap(UserBrief::getId, b -> b.getNickname() == null ? "" : b.getNickname(), (a, b) -> a));
        } catch (Exception e) {
            log.warn("[item] 批量查询创作者昵称失败，降级为空", e);
            return Collections.emptyMap();
        }
    }

    private String safeReadNickname(Long creatorId) {
        try {
            return userReadMapper.selectNicknameById(creatorId);
        } catch (Exception e) {
            log.warn("[item] 查询创作者昵称失败 creatorId={}", creatorId, e);
            return null;
        }
    }

    private TryonMode resolveTryonMode(String tryonMode) {
        return TryonMode.of(tryonMode);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        return JSONUtil.toJsonStr(value);
    }
}
