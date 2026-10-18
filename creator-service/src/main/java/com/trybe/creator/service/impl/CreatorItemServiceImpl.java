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
import com.trybe.creator.domain.enums.CreatorItemStatus;
import com.trybe.creator.domain.enums.GarmentCategoryL1;
import com.trybe.creator.domain.enums.TryonMode;
import com.trybe.creator.domain.event.ItemPublishedEvent;
import com.trybe.creator.domain.po.CreatorItem;
import com.trybe.creator.domain.vo.ItemDetailVO;
import com.trybe.creator.domain.vo.ItemListVO;
import com.trybe.creator.domain.vo.ModelImageVO;
import com.trybe.creator.mapper.CreatorItemMapper;
import com.trybe.creator.service.ICreatorItemService;
import com.trybe.creator.service.IModelImageService;
import com.trybe.creator.util.CreatorOssPathBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 创作者物品服务实现
 *
 * <p>状态机：DRAFT --I6 发布--> ON_SHELF --I8 下架--> OFF_SHELF --I7 上架--> ON_SHELF；
 * 发布即上架，不再经过人工审核环节。
 *
 * <p>事务边界：纯 DB 的状态流转加 @Transactional；
 * 涉及 OSS/人脸等外部调用的流程在 ModelImageServiceImpl / UploadService 中，均无事务（红线①）。
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreatorItemServiceImpl extends ServiceImpl<CreatorItemMapper, CreatorItem>
        implements ICreatorItemService {

    private final ItemNoGenerator itemNoGenerator;
    private final OssService ossService;
    private final IModelImageService modelImageService;
    private final ApplicationEventPublisher eventPublisher;

    // ==================== I1 我的物品列表 ====================

    @Override
    public PageDTO<ItemListVO> pageMyItems(Long creatorId, ItemPageQuery query) {
        LambdaQueryWrapper<CreatorItem> wrapper = buildQueryWrapper(creatorId, query);
        return toPageDTO(page(query.toMpPageDefaultSortByCreateTimeDesc(), wrapper));
    }

    // ==================== I2 我的物品详情 ====================

    @Override
    public ItemDetailVO getMyItem(Long creatorId, Long id) {
        CreatorItem item = getOwnedItem(creatorId, id);
        return toDetailVO(item);
    }

    // ==================== I3 新建（DRAFT） ====================

    @Override
    @Transactional
    public ItemDetailVO createItem(Long creatorId, ItemSaveReq req) {
        if (StrUtil.isBlank(req.getTitle())) {
            throw new BadRequestException("作品标题不能为空");
        }
        GarmentCategoryL1 category = GarmentCategoryL1.strictOf(req.getCategoryCode());

        CreatorItem item = new CreatorItem()
                .setItemNo(itemNoGenerator.nextItemNo())
                .setCreatorId(creatorId)
                .setCategoryCode(category.getCode())
                .setTitle(StrUtil.trim(req.getTitle()))
                .setDescription(StrUtil.trimToNull(req.getDescription()))
                .setLinkUrl(StrUtil.trimToNull(req.getLinkUrl()))
                .setStatus(CreatorItemStatus.DRAFT.getCode())
                .setTryonMode(resolveTryonMode(req.getTryonMode()).getCode())
                .setPatternTags(toJson(req.getPatternTags()))
                // 新建一定是「未经 AI 解析」：发布成功后才由后台回填，并置为 1
                .setAiParsed(0)
                .setZone(StrUtil.trimToNull(req.getZone()))
                .setTopics(toJson(req.getTopics()))
                .setImageKey(requireOwnImageKey(creatorId, req.getImageKey()))
                // 创作者手填的「季节 / 衣服款式 / 材质成分」：
                // 与 AI 的 aiSeasons / aiStyles / aiMaterial 是两份独立数据，各存各的列
                .setSeasons(toJson(req.getSeasons()))
                .setStyles(toJson(req.getStyles()))
                .setMaterial(StrUtil.trimToNull(req.getMaterial()));
        save(item);
        log.info("[item] 作品已创建 id={} itemNo={} creatorId={}", item.getId(), item.getItemNo(), creatorId);
        return toDetailVO(item);
    }

    // ==================== I4 编辑（null = 不修改） ====================

    /**
     * 编辑作品。
     *
     * <p><b>只接受创作者能管的字段</b>：标题 / 描述 / 链接 / 分区 / 标签 / 话题 /
     * 季节 / 衣服款式 / 材质成分 / 主图。
     * 请求体里已经没有别的可写字段（见 {@link ItemSaveReq}），所以这里天然不可能
     * 被用来改 AI 解析结果（{@code ai_} 列）或试穿标定数据。
     *
     * <p><b>增删图片不走这里</b>：走 {@code POST /creator/items/{id}/model-images}
     * 与 {@code DELETE /creator/model-images/{imageId}}。
     * 换主图额外做归属校验：{@code imageKey} 必须命中该作品已绑定的图片，
     * 否则创作者可以把主图指向任意 OSS 路径（甚至别人的图）。
     */
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
        // 描述与链接允许传空串清空，所以判 null 而不是判空
        if (req.getDescription() != null) {
            updateWrapper.set(CreatorItem::getDescription, StrUtil.trimToNull(req.getDescription()));
            changed = true;
        }
        if (req.getLinkUrl() != null) {
            updateWrapper.set(CreatorItem::getLinkUrl, StrUtil.trimToNull(req.getLinkUrl()));
            changed = true;
        }
        if (StrUtil.isNotBlank(req.getZone())) {
            updateWrapper.set(CreatorItem::getZone, StrUtil.trim(req.getZone()));
            changed = true;
        }
        if (req.getPatternTags() != null) {
            updateWrapper.set(CreatorItem::getPatternTags, toJson(req.getPatternTags()));
            changed = true;
        }
        if (req.getTopics() != null) {
            updateWrapper.set(CreatorItem::getTopics, toJson(req.getTopics()));
            changed = true;
        }
        // 创作者手填的三个字段：只写无前缀列，绝不碰 aiSeasons / aiStyles / aiMaterial
        if (req.getSeasons() != null) {
            updateWrapper.set(CreatorItem::getSeasons, toJson(req.getSeasons()));
            changed = true;
        }
        if (req.getStyles() != null) {
            updateWrapper.set(CreatorItem::getStyles, toJson(req.getStyles()));
            changed = true;
        }
        if (req.getMaterial() != null) {
            updateWrapper.set(CreatorItem::getMaterial, StrUtil.trimToNull(req.getMaterial()));
            changed = true;
        }
        if (StrUtil.isNotBlank(req.getImageKey())) {
            String imageKey = StrUtil.trim(req.getImageKey());
            requireOwnedImage(id, imageKey);
            updateWrapper.set(CreatorItem::getImageKey, imageKey);
            changed = true;
        }

        if (changed) {
            update(updateWrapper);
        }
        log.info("[item] 作品已更新 id={} creatorId={} changed={}", id, creatorId, changed);
        return toDetailVO(getById(id));
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

    // ==================== I6 发布（发布即上架） ====================

    @Override
    @Transactional
    public void publish(Long creatorId, Long id) {
        CreatorItem item = getOwnedItem(creatorId, id);
        CreatorItemStatus status = CreatorItemStatus.of(item.getStatus());
        if (status == null || !status.publishable()) {
            throw new BizIllegalException(ErrorCodeEnum.STATUS_ERROR, "当前状态不可发布，仅草稿/已下架可发布");
        }
        validatePublishable(item);

        updateStatus(id, CreatorItemStatus.ON_SHELF);
        log.info("[item] 物品已发布 id={} creatorId={}，已投递商品信息解析事件", id, creatorId);
        // 异步解析商品信息：监听器挂在 AFTER_COMMIT 上，保证此时上架已落库
        eventPublisher.publishEvent(new ItemPublishedEvent(creatorId, id));
    }

    // ==================== I7 / I8 上下架 ====================

    @Override
    @Transactional
    public void onShelf(Long creatorId, Long id) {
        CreatorItem item = getOwnedItem(creatorId, id);
        CreatorItemStatus status = CreatorItemStatus.of(item.getStatus());
        if (status == null || !status.canOnShelf()) {
            throw new BizIllegalException(ErrorCodeEnum.STATUS_ERROR, "仅已下架的物品可上架");
        }
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
        updateStatus(id, CreatorItemStatus.OFF_SHELF);
        log.info("[item] 物品已下架 id={} creatorId={}", id, creatorId);
    }

    // ==================== 私有方法 ====================

    /**
     * 构建查询条件：始终限定当前创作者自己的作品
     */
    private LambdaQueryWrapper<CreatorItem> buildQueryWrapper(Long creatorId, ItemPageQuery query) {
        LambdaQueryWrapper<CreatorItem> wrapper = new LambdaQueryWrapper<CreatorItem>()
                .eq(CreatorItem::getCreatorId, creatorId);
        if (query != null) {
            if (StrUtil.isNotBlank(query.getStatus())) {
                wrapper.eq(CreatorItem::getStatus, query.getStatus().trim());
            }
            if (StrUtil.isNotBlank(query.getKeyword())) {
                String kw = query.getKeyword().trim();
                // 与列表页搜索框的承诺一致：标题 / 描述 / 分区 / 标签 / 款式 / 材质 任一命中即可。
                // 刻意**不含**任何 ai_ 列：否则创作者能用关键词二分试探反推出 AI 的解析结果。
                wrapper.and(w -> w.like(CreatorItem::getTitle, kw)
                        .or().like(CreatorItem::getDescription, kw)
                        .or().like(CreatorItem::getZone, kw)
                        .or().like(CreatorItem::getPatternTags, kw)
                        .or().like(CreatorItem::getStyles, kw)
                        .or().like(CreatorItem::getMaterial, kw));
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
     * 发布前置校验：标题/分类/主图必填；试穿模式含模特时必须存在已授权的模特图
     */
    private void validatePublishable(CreatorItem item) {
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

    private PageDTO<ItemListVO> toPageDTO(Page<CreatorItem> page) {
        if (CollUtils.isEmpty(page.getRecords())) {
            return PageDTO.empty(page);
        }
        List<ItemListVO> voList = page.getRecords().stream()
                .map(item -> {
                    ItemListVO vo = BeanUtils.copyBean(item, ItemListVO.class);
                    vo.setId(item.getId());
                    vo.setImageUrl(presignUrl(item.getImageKey()));
                    return vo;
                })
                .collect(Collectors.toList());
        return PageDTO.of(page, voList);
    }

    private ItemDetailVO toDetailVO(CreatorItem item) {
        ItemDetailVO vo = BeanUtils.copyBean(item, ItemDetailVO.class);
        vo.setId(item.getId());
        vo.setImageUrl(presignUrl(item.getImageKey()));
        vo.setModelImages(modelImageService.listByItem(item.getId()));
        return vo;
    }

    /**
     * 新建时的主图必须是该创作者自己 OSS 空间里的对象。
     *
     * <p>新建这一刻作品还没有模特图记录，所以没法用「是否属于本作品图片」来校验
     * （那是编辑换主图时的口径）。退一步卡住最外层的边界：路径前缀必须是
     * {@code creator/{自己userId}/}。否则创作者可以建一篇指向别人的图的作品。
     */
    private String requireOwnImageKey(Long creatorId, String imageKey) {
        String key = StrUtil.trimToNull(imageKey);
        if (key == null) {
            return null;
        }
        if (!CreatorOssPathBuilder.belongsTo(creatorId, key)) {
            throw new BadRequestException("主图路径不合法");
        }
        return key;
    }

    /**
     * 换主图只能从该作品已绑定的图片里挑。
     *
     * <p>「图片不可修改」必须由服务端兜底：前端不给上传入口 ≠ 接口改不了。
     * 不做这层校验，创作者直接调接口就能把主图指向任意 OSS 路径（含他人的图），
     * 等于绕过了「作品图片只能 1 张或 4 张、且必须经过肖像授权」的全部约束。
     */
    private void requireOwnedImage(Long itemId, String imageKey) {
        boolean owned = modelImageService.listByItem(itemId).stream()
                .map(ModelImageVO::getImageKey)
                .anyMatch(imageKey::equals);
        if (!owned) {
            throw new BadRequestException("主图必须从该作品已上传的图片中选择");
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
