package com.trybe.creator.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.trybe.common.ai.GarmentAiProperties;
import com.trybe.common.ai.GarmentCategory;
import com.trybe.common.ai.GarmentRecognizeResult;
import com.trybe.common.ai.GarmentRecognizeService;
import com.trybe.common.service.OssService;
import com.trybe.common.utils.FileTypeUtils;
import com.trybe.common.utils.ImageUtils;
import com.trybe.creator.domain.po.CreatorItem;
import com.trybe.creator.domain.po.CreatorItemModelImage;
import com.trybe.creator.mapper.CreatorItemMapper;
import com.trybe.creator.mapper.CreatorItemModelImageMapper;
import com.trybe.creator.service.IGarmentInfoFillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 商品信息自动回填实现
 *
 * <p>执行链路：发布事务提交 → {@code ItemPublishedEvent} → 本服务（异步线程池）
 * → 读 OSS 图 → 压缩 → Qwen-VL 识别 → 回填 creator_item 的 {@code ai_} 列。
 *
 * <p><b>本类最重要的一条规则：只写 {@code ai_} 前缀的列。</b>
 * 创作者手填的 title / description / linkUrl / zone / patternTags / seasons / styles /
 * topics / material / categoryCode / imageKey 一个都不碰。
 * 两边的「季节 / 款式 / 材质 / 分类」是<b>两份独立数据</b>，各存各的列、不合并，
 * 这样创作者视图永远不会被平台数据污染，也不存在「谁覆盖谁」的歧义。
 * 见 sql/creatorService/creator_item_字段改造.sql。
 *
 * <p><b>另外三个刻意的取舍</b>：
 * <ol>
 *   <li><b>不用 @Transactional</b>：中间有 30s 级外部 HTTP 调用，长事务会占着连接池（项目红线①）。</li>
 *   <li><b>只跑一次</b>：更新条件带 {@code ai_parsed = 0}，并发触发两次只有一次能写入。</li>
 *   <li><b>失败静默</b>：作品已经发布上线，模型侧任何问题都不应升级成用户可见的错误，
 *       ai_parsed 保持 0，创作者重新发布一次即可重试。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GarmentInfoFillServiceImpl implements IGarmentInfoFillService {

    /** 送模型图片的兜底体积上限（未配置 max-image-bytes 时使用） */
    private static final int DEFAULT_MAX_IMAGE_BYTES = 4194304;

    /**
     * 标签数量上限：模型常把同一个词同时塞进 tags / pattern / fit / styles / style 五个字段，
     * 去重后合并写入 ai_styles。该列是 VARCHAR(512)，12 个短标签的 JSON 串约 100 字符。
     */
    private static final int MAX_TAGS = 12;

    /** 与 creator_item 各 ai_ 列长度对齐，避免截断失败导致整条更新报错 */
    private static final int MAX_AI_PRODUCT_NAME = 128;
    private static final int MAX_AI_CATEGORY_CODE = 32;
    private static final int MAX_AI_COLOR_HEX = 16;
    private static final int MAX_AI_COLOR_NAME = 32;
    private static final int MAX_AI_MATERIAL = 128;
    private static final int MAX_AI_SELLING_POINTS = 40;

    private final CreatorItemMapper creatorItemMapper;
    private final CreatorItemModelImageMapper modelImageMapper;
    private final OssService ossService;
    private final GarmentAiProperties aiProperties;
    private final ObjectProvider<GarmentRecognizeService> recognizeServiceProvider;

    @Override
    public void fillFromItemImage(Long creatorId, Long itemId) {
        CreatorItem item = creatorItemMapper.selectById(itemId);
        if (item == null) {
            log.warn("[garment-fill] 作品不存在，跳过 itemId={}", itemId);
            return;
        }
        if (Integer.valueOf(1).equals(item.getAiParsed())) {
            log.info("[garment-fill] 已解析过，跳过 itemId={}", itemId);
            return;
        }
        if (Boolean.FALSE.equals(aiProperties.getEnabled())) {
            log.info("[garment-fill] AI 识别已关闭，跳过 itemId={}", itemId);
            return;
        }
        GarmentRecognizeService recognizeService = recognizeServiceProvider.getIfAvailable();
        if (recognizeService == null) {
            log.warn("[garment-fill] 未配置可用的识别模型，跳过 itemId={}", itemId);
            return;
        }

        String imageKey = resolveSourceImageKey(item);
        if (StrUtil.isBlank(imageKey)) {
            log.warn("[garment-fill] 作品没有可用于解析的图片，跳过 itemId={}", itemId);
            return;
        }

        long startMs = System.currentTimeMillis();
        try {
            byte[] originBytes = ossService.readObjectBytes(ossService.defaultBucketName(), imageKey);
            if (originBytes == null || originBytes.length == 0) {
                log.warn("[garment-fill] 图片内容为空，跳过 itemId={} imageKey={}", itemId, imageKey);
                return;
            }
            int maxBytes = aiProperties.getMaxImageBytes() == null
                    ? DEFAULT_MAX_IMAGE_BYTES : aiProperties.getMaxImageBytes();
            GarmentRecognizeResult result = recognizeService.recognize(
                    ImageUtils.compressForModel(originBytes, maxBytes),
                    FileTypeUtils.contentTypeOfFile(imageKey));

            boolean applied = applyToItem(item, result, recognizeService.modelName());
            log.info("[garment-fill] 回填{} itemId={} creatorId={} imageKey={} aiCategory={} costMs={}",
                    applied ? "完成" : "跳过(已被更新)", itemId, creatorId, imageKey,
                    result.getCategoryL1(), System.currentTimeMillis() - startMs);
        } catch (Exception e) {
            // 后台任务绝不外抛：作品已发布，解析失败只是少了推广信息，重新发布可重试
            log.error("[garment-fill] 回填失败，作品保持原样 itemId={} creatorId={} imageKey={}",
                    itemId, creatorId, imageKey, e);
        }
    }

    /**
     * 把识别结果写入作品的 {@code ai_} 列。
     *
     * <p><b>写：</b>aiProductName / aiCategoryCode / aiColorHex / aiColorName /
     * aiSeasons / aiStyles / aiMaterial / aiSellingPoints / aiSuggestedPrice / aiParsed*。
     *
     * <p><b>绝不写：</b>title、description、linkUrl、zone、patternTags、seasons、styles、
     * topics、material、categoryCode、imageKey、price、status。
     * 特别注意 {@code seasons} / {@code styles} / {@code material} / {@code categoryCode}
     * 这四列 —— 它们<b>是创作者的字段</b>，AI 的同名判断结果各存一份在 ai_ 列里。
     *
     * <p>更新条件带 {@code ai_parsed = 0}：即便同一作品被并发触发两次，
     * 也只有一次能真正写入（另一个拿到 0 行受影响，直接跳过）。
     *
     * @param item 发布时读取到的那一版作品快照（ai_parsed 必为 0）
     * @return true 表示本次确实写入
     */
    private boolean applyToItem(CreatorItem item, GarmentRecognizeResult result, String modelName) {
        LambdaUpdateWrapper<CreatorItem> update = new LambdaUpdateWrapper<CreatorItem>()
                .eq(CreatorItem::getId, item.getId())
                .eq(CreatorItem::getAiParsed, 0);

        // 逐字段判空：模型对个别字段返回 null 时保留原值，不用空值去覆盖
        setIfPresent(update, CreatorItem::getAiProductName,
                truncate(result.getTitle(), MAX_AI_PRODUCT_NAME));
        setIfPresent(update, CreatorItem::getAiCategoryCode,
                truncate(GarmentCategory.of(result.getCategoryL1()).getCode(), MAX_AI_CATEGORY_CODE));
        setIfPresent(update, CreatorItem::getAiColorName,
                truncate(result.getColor(), MAX_AI_COLOR_NAME));
        setIfPresent(update, CreatorItem::getAiColorHex,
                truncate(result.getColorHex(), MAX_AI_COLOR_HEX));
        setIfPresent(update, CreatorItem::getAiMaterial,
                truncate(result.getMaterial(), MAX_AI_MATERIAL));
        setIfPresent(update, CreatorItem::getAiSellingPoints,
                truncate(result.getSellingPoints(), MAX_AI_SELLING_POINTS));

        // 模型常把同一个词塞进多个字段，LinkedHashSet 顺手去重
        String aiStyles = toJson(merge(result.getTags(), result.getStyles(),
                result.getPattern(), result.getFit(), result.getStyle()));
        if (aiStyles != null) {
            update.set(CreatorItem::getAiStyles, aiStyles);
        }
        String aiSeasons = toJson(result.getSeasons());
        if (aiSeasons != null) {
            update.set(CreatorItem::getAiSeasons, aiSeasons);
        }
        if (result.getSuggestedPrice() != null) {
            update.set(CreatorItem::getAiSuggestedPrice, result.getSuggestedPrice());
        }

        update.set(CreatorItem::getAiParsed, 1);
        update.set(CreatorItem::getAiModel, modelName);
        update.set(CreatorItem::getAiParsedAt, LocalDateTime.now());
        return creatorItemMapper.update(null, update) > 0;
    }

    /**
     * 解析用哪张图：优先作品主图，其次第一张模特图
     *
     * <p>主图一定是创作者指定的那一张；主图缺失（历史数据或其他入口创建的作品）
     * 时退到模特图，保证「有图就能解析」。
     */
    private String resolveSourceImageKey(CreatorItem item) {
        if (StrUtil.isNotBlank(item.getImageKey())) {
            return item.getImageKey();
        }
        List<CreatorItemModelImage> images = modelImageMapper.selectList(
                new LambdaQueryWrapper<CreatorItemModelImage>()
                        .eq(CreatorItemModelImage::getItemId, item.getId())
                        .orderByAsc(CreatorItemModelImage::getSortOrder)
                        .orderByAsc(CreatorItemModelImage::getId)
                        .last("LIMIT 1"));
        return images == null || images.isEmpty() ? null : images.get(0).getImageKey();
    }

    /**
     * 合并模型返回的标签并去重。
     *
     * @param listA   模型返回的标签数组（tags）
     * @param listB   模型返回的标签数组（styles），元素可为 null
     * @param singles 模型返回的单值标签字段（pattern、fit、style 这类），可为空
     */
    private List<String> merge(List<String> listA, List<String> listB, String... singles) {
        Set<String> merged = new LinkedHashSet<>();
        addAll(merged, listA);
        addAll(merged, listB);
        for (String value : singles) {
            addIfPresent(merged, value);
        }
        List<String> result = new ArrayList<>(merged);
        return result.size() > MAX_TAGS ? new ArrayList<>(result.subList(0, MAX_TAGS)) : result;
    }

    private void addAll(Set<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            addIfPresent(target, value);
        }
    }

    private void addIfPresent(Set<String> target, String value) {
        if (StrUtil.isNotBlank(value)) {
            target.add(value.trim());
        }
    }

    private String toJson(List<String> values) {
        return values == null || values.isEmpty() ? null : JSONUtil.toJsonStr(values);
    }

    private String truncate(String value, int maxLength) {
        return StrUtil.isBlank(value) ? null : StrUtil.maxLength(value.trim(), maxLength);
    }

    private <T> void setIfPresent(LambdaUpdateWrapper<CreatorItem> update,
                                  SFunction<CreatorItem, T> column,
                                  T value) {
        if (value != null) {
            update.set(column, value);
        }
    }
}
