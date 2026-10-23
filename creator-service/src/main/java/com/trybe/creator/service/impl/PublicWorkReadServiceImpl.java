package com.trybe.creator.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trybe.api.dto.WorkBriefDTO;
import com.trybe.api.dto.WorkDetailDTO;
import com.trybe.api.dto.WorkQuery;
import com.trybe.common.domain.PageDTO;
import com.trybe.common.domain.PageQuery;
import com.trybe.common.exception.BadRequestException;
import com.trybe.common.service.OssService;
import com.trybe.common.utils.CollUtils;
import com.trybe.creator.domain.enums.CreatorItemStatus;
import com.trybe.creator.domain.enums.WorkZone;
import com.trybe.creator.domain.po.CreatorItem;
import com.trybe.creator.domain.po.AccountBrief;
import com.trybe.creator.domain.po.CreatorItem;
import com.trybe.creator.domain.po.CreatorItemModelImage;
import com.trybe.creator.mapper.AccountBriefMapper;
import com.trybe.creator.mapper.CreatorItemMapper;
import com.trybe.creator.mapper.CreatorItemModelImageMapper;
import com.trybe.creator.service.IPublicWorkReadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.ZoneId;

/**
 * 作品公开读服务实现
 *
 * <p><b>只出 {@code ON_SHELF}</b>：草稿与已下架作品对 C 端不可见（逻辑删除由 MyBatis-Plus
 * {@code @TableLogic} 自动带上 {@code deleted = 0}）。
 *
 * <p><b>查询条件只落在创作者公开发布时自己填的列</b>（标题/描述/标签/款式/分区），
 * 与创作者列表页一致，绝不碰任何 {@code ai_} 列 —— 否则 C 端用户可以用关键词
 * 二分试探把 AI 解析结果「猜」出来。
 *
 * <p>图片：OSS key 是持久化事实，访问 URL 每次现算（预签名，有效期 {@link #COVER_URL_EXPIRE_SECONDS}），
 * 签名失败降级为 null 而不是让整个列表失败（前端渲染占位图）。
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicWorkReadServiceImpl implements IPublicWorkReadService {

    /**
     * 作品图签名 URL 有效期（秒）
     *
     * <p>作品图存<b>私有桶</b>，只能靠短时签名访问（与抖音同款思路：私有存储 + 带时效的
     * 签名 URL，而不是把桶设成公共读 —— 公共读一旦桶策略允许 List，等于把整桶可枚举）。
     *
     * <p><b>URL 绝不落库、也不允许前端持久化</b>：DB 只存 objectKey，URL 每次响应现算。
     * 前端缓存「数据」可以，缓存「URL」不行 —— 这是 1 小时图裂的唯一成因。
     */
    private static final long WORK_IMAGE_TTL_SECONDS = 3600L;

    /** 创作者未建档案时的兜底昵称 */
    private static final String DEFAULT_CREATOR_NICKNAME = "TryBe创作者";

    private final CreatorItemMapper creatorItemMapper;
    private final AccountBriefMapper accountBriefMapper;
    private final CreatorItemModelImageMapper creatorItemModelImageMapper;
    private final OssService ossService;

    @Override
    public PageDTO<WorkBriefDTO> pagePublicWorks(WorkQuery query) {
        WorkQuery safeQuery = (query == null ? new WorkQuery() : query).normalize();

        LambdaQueryWrapper<CreatorItem> wrapper = new LambdaQueryWrapper<CreatorItem>()
                .eq(CreatorItem::getStatus, CreatorItemStatus.ON_SHELF.getCode());
        if (safeQuery.getZone() != null) {
            if (!WorkZone.isValid(safeQuery.getZone())) {
                throw new BadRequestException("无效的分区: " + safeQuery.getZone());
            }
            wrapper.eq(CreatorItem::getZone, safeQuery.getZone());
        }
        String keyword = safeQuery.getKeyword();
        if (keyword != null) {
            wrapper.and(w -> w.like(CreatorItem::getTitle, keyword)
                    .or().like(CreatorItem::getDescription, keyword)
                    .or().like(CreatorItem::getPatternTags, keyword)
                    .or().like(CreatorItem::getStyles, keyword));
        }

        // WorkQuery 不继承 PageQuery（Feign 契约模块不引持久层依赖），这里转成 MP 分页参数
        PageQuery pageQuery = new PageQuery()
                .setPageNo(safeQuery.getPageNo())
                .setPageSize(safeQuery.getPageSize());
        Page<CreatorItem> page = creatorItemMapper.selectPage(
                pageQuery.toMpPageDefaultSortByCreateTimeDesc(), wrapper);
        if (CollUtils.isEmpty(page.getRecords())) {
            return PageDTO.empty(page);
        }

        Map<Long, AccountBrief> accountMap = loadAccounts(page.getRecords());
        List<WorkBriefDTO> list = page.getRecords().stream()
                .map(item -> toBriefDTO(item, accountMap.get(item.getCreatorId())))
                .collect(Collectors.toList());
        return PageDTO.of(page, list);
    }

    @Override
    public WorkDetailDTO getPublicWorkDetail(Long id) {
        if (id == null) {
            return null;
        }
        CreatorItem item = creatorItemMapper.selectOne(new LambdaQueryWrapper<CreatorItem>()
                .eq(CreatorItem::getId, id)
                .eq(CreatorItem::getStatus, CreatorItemStatus.ON_SHELF.getCode()));
        if (item == null) {
            return null;
        }
        AccountBrief account = accountBriefMapper.selectBriefById(item.getCreatorId());

        WorkDetailDTO dto = new WorkDetailDTO();
        dto.setId(item.getId());
        dto.setItemNo(item.getItemNo());
        dto.setTitle(item.getTitle());
        dto.setDescription(item.getDescription());
        dto.setZone(item.getZone());
        dto.setImageUrl(workImageUrl(item.getImageKey()));
        dto.setImages(buildDetailImages(item));
        dto.setTags(parseJsonList(item.getPatternTags()));
        dto.setSeasons(parseJsonList(item.getSeasons()));
        dto.setStyles(parseJsonList(item.getStyles()));
        dto.setTopics(parseJsonList(item.getTopics()));
        dto.setMaterial(item.getMaterial());
        dto.setCreatorId(item.getCreatorId());
        dto.setCreatorNickname(account == null || StrUtil.isBlank(account.getNickname())
                ? DEFAULT_CREATOR_NICKNAME : account.getNickname());
        dto.setCreatorAvatarUrl(avatarAccessUrl(account));
        dto.setCreatorBio(account == null ? null : account.getBio());
        dto.setPublishTime(item.getCreateTime());
        return dto;
    }

    @Override
    public List<String> listZones() {
        return WorkZone.codes();
    }

    // ==================== 私有方法 ====================

    /**
     * 批量取创作者公开档案（一次 in 查询，避免 N+1）
     */
    private Map<Long, AccountBrief> loadAccounts(List<CreatorItem> items) {
        Set<Long> creatorIds = items.stream()
                .map(CreatorItem::getCreatorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (CollUtils.isEmpty(creatorIds)) {
            return Collections.emptyMap();
        }
        List<AccountBrief> accounts = accountBriefMapper.selectBriefByIds(creatorIds);
        if (CollUtils.isEmpty(accounts)) {
            return Collections.emptyMap();
        }
        return accounts.stream()
                .filter(a -> a.getId() != null)
                .collect(Collectors.toMap(AccountBrief::getId, Function.identity(), (a, b) -> a));
    }

    /**
     * 详情页图集：模特图按 sortOrder 升序，封面置顶且去重
     *
     * <p>没有任何模特图时（只传了一张封面的作品），图集退化为「封面这一张」，
     * 而不是空数组 —— 详情页的轮播至少要有一张图可渲染。
     */
    private List<String> buildDetailImages(CreatorItem item) {
        List<String> urls = new ArrayList<>();
        List<CreatorItemModelImage> modelImages = creatorItemModelImageMapper.selectList(
                new LambdaQueryWrapper<CreatorItemModelImage>()
                        .eq(CreatorItemModelImage::getItemId, item.getId())
                        .orderByAsc(CreatorItemModelImage::getSortOrder)
                        .orderByAsc(CreatorItemModelImage::getId));
        if (!CollUtils.isEmpty(modelImages)) {
            for (CreatorItemModelImage image : modelImages) {
                String url = workImageUrl(image.getImageKey());
                if (StrUtil.isNotBlank(url)) {
                    urls.add(url);
                }
            }
        }
        String cover = workImageUrl(item.getImageKey());
        if (StrUtil.isNotBlank(cover)) {
            urls.remove(cover);
            urls.add(0, cover);
        }
        return urls;
    }

    private WorkBriefDTO toBriefDTO(CreatorItem item, AccountBrief account) {
        WorkBriefDTO dto = new WorkBriefDTO();
        dto.setId(item.getId());
        dto.setItemNo(item.getItemNo());
        dto.setTitle(item.getTitle());
        dto.setDescription(item.getDescription());
        dto.setZone(item.getZone());
        dto.setImageUrl(workImageUrl(item.getImageKey()));
        dto.setTags(parseJsonList(item.getPatternTags()));
        dto.setSeasons(parseJsonList(item.getSeasons()));
        dto.setStyles(parseJsonList(item.getStyles()));
        dto.setCreatorId(item.getCreatorId());
        dto.setCreatorNickname(account == null || StrUtil.isBlank(account.getNickname())
                ? DEFAULT_CREATOR_NICKNAME : account.getNickname());
        dto.setCreatorAvatarUrl(avatarAccessUrl(account));
        dto.setPublishTime(item.getCreateTime());
        return dto;
    }

    /**
     * JSON 数组字符串 → List；脏数据一律降级为空列表，不打断整页渲染
     */
    private List<String> parseJsonList(String json) {
        if (StrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            return JSONUtil.parseArray(json).toList(String.class);
        } catch (Exception e) {
            log.warn("[public-work] 标签JSON解析失败，降级为空列表 json={}", json);
            return Collections.emptyList();
        }
    }

    /**
     * 作品图访问 URL（私有桶 + 短时签名）
     *
     * <p>唯一的 URL 生成入口：详情图集、列表封面都走这里，禁止在别处手写 presign，
     * 否则 TTL 与降级策略会分叉。
     *
     * @param objectKey OSS objectKey（DB 里持久化的事实）
     * @return 签名 URL；key 为空或签名失败返回 null（前端渲染占位图）
     */
    private String workImageUrl(String objectKey) {
        if (StrUtil.isBlank(objectKey)) {
            return null;
        }
        try {
            return ossService.generatePresignedUrl(objectKey, WORK_IMAGE_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("[public-work] 生成作品图URL失败，降级为 null objectKey={}", objectKey, e);
            return null;
        }
    }

    /**
     * 创作者头像访问 URL
     *
     * <p>头像统一存在 {@code app_user.avatar}（账号体系 v3），小程序与后台共用同一份。
     *
     * <p><b>头像归档在第二 bucket（{@code trybe-avatar}，公共读），必须用与上传一致的
     * bucket 取 URL</b>：上传侧 {@code UploadService} 与创作者后台
     * {@code CreatorProfileServiceImpl#buildVO} 都走第二桶，只有本类曾经错走默认桶 ——
     * 拿默认桶去签第二桶的对象，OSS 直接 404（2026-10-17 踩坑：封面 200 头像 404，
     * 头像其实上传成功了）。封面图仍在默认桶，{@link #presign} 不动。
     *
     * <p>取法与 {@code buildVO} 保持一致：{@code accessUrl} 对公共读桶下发永久 URL
     * （无签名、不过期），私有桶才回落预签名；版本号取 {@code update_time} 毫秒
     * （换图后才会变），用于打破小程序侧图片缓存。
     */
    private String avatarAccessUrl(AccountBrief account) {
        if (account == null || StrUtil.isBlank(account.getAvatar())) {
            return null;
        }
        try {
            Long version = account.getUpdateTime() == null ? null
                    : account.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            return ossService.accessUrl(ossService.secondBucketName(), account.getAvatar(), version);
        } catch (Exception e) {
            log.warn("[public-work] 生成头像访问URL失败，降级为 null avatarKey={}", account.getAvatar(), e);
            return null;
        }
    }
}
