package com.trybe.wardrobe.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.trybe.common.ai.GarmentRecognizeResult;
import com.trybe.common.service.OssService;
import com.trybe.wardrobe.domain.dto.UpdateWardrobeReq;
import com.trybe.wardrobe.domain.enums.GarmentCategoryL1;
import com.trybe.wardrobe.domain.enums.WardrobeSource;
import com.trybe.wardrobe.domain.enums.WearStatus;
import com.trybe.wardrobe.domain.po.Product;
import com.trybe.wardrobe.domain.po.WardrobeItem;
import com.trybe.wardrobe.domain.vo.WardrobeItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 衣柜条目 PO ↔ VO 转换器
 *
 * <p>集中处理三件事，避免在多个 Service 里各写一份导致行为漂移：
 * <ol>
 *   <li>OSS objectKey → 预签名访问 URL</li>
 *   <li>JSON 数组列 ↔ List 字段</li>
 *   <li>枚举码 → 中文展示名</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WardrobeItemConverter {

    /** 季节码 → 中文名，顺序即为前端展示顺序 */
    private static final Map<String, String> SEASON_NAMES = new LinkedHashMap<String, String>() {{
        put("SPRING", "春季");
        put("SUMMER", "夏季");
        put("AUTUMN", "秋季");
        put("WINTER", "冬季");
    }};

    private final OssService ossService;

    // ==================== PO → VO ====================

    public WardrobeItemVO toVO(WardrobeItem item, Product product) {
        WardrobeItemVO vo = new WardrobeItemVO();
        vo.setWardrobeId(item.getId());
        vo.setSource(item.getSource());
        vo.setProductId(item.getProductId());
        vo.setTitle(item.getTitle());
        vo.setRemark(item.getRemark());
        vo.setCreateTime(item.getCreateTime());

        // 图片
        vo.setImageUrl(presign(item.getImageKey()));
        vo.setOriginImageUrl(presign(item.getOriginImageKey()));
        vo.setCutoutImageUrl(presign(item.getCutoutImageKey()));
        vo.setDetailImageUrls(presignList(toStringList(item.getDetailImageKeys())));

        // 分类
        vo.setCategoryL1(item.getCategoryL1());
        vo.setCategoryL1Name(categoryL1Name(item.getCategoryL1()));
        vo.setCategoryL2(item.getCategoryL2());
        String name = fullCategoryName(item.getCategoryL1(), item.getCategoryL2());
        vo.setCategoryName(name);
        vo.setCategoryId(item.getCategoryId());

        // 颜色
        vo.setColor(item.getColor());
        vo.setColorHex(item.getColorHex());
        List<String> colors = toStringList(item.getColors());
        if (colors.isEmpty() && StrUtil.isNotBlank(item.getColor())) {
            colors = Collections.singletonList(item.getColor());
        }
        vo.setColors(colors);

        // 季节
        List<String> seasons = parseSeasons(item.getSeasons());
        vo.setSeasons(seasons);
        List<String> seasonNames = new ArrayList<>();
        for (String season : seasons) {
            seasonNames.add(SEASON_NAMES.getOrDefault(season, season));
        }
        vo.setSeasonNames(seasonNames);

        // 状态
        WearStatus wearStatus = WearStatus.of(item.getWearStatus());
        vo.setWearStatus(wearStatus.getValue());
        vo.setWearStatusText(wearStatus.getDesc());

        // 购物信息
        vo.setBrand(item.getBrand());
        vo.setPrice(item.getPrice());
        vo.setWearCount(item.getWearCount() == null ? 0 : item.getWearCount());
        vo.setCostPerWear(item.getCostPerWear());
        vo.setPurchaseDate(item.getPurchaseDate());

        // 推荐扩展字段
        vo.setStyle(item.getStyle());
        vo.setStyles(toStringList(item.getStyles()));
        vo.setMaterial(item.getMaterial());
        vo.setOccasions(toStringList(item.getOccasions()));
        vo.setSize(item.getSize());
        vo.setFit(item.getFit());
        vo.setPattern(item.getPattern());
        vo.setThickness(item.getThickness());
        vo.setWarmth(item.getWarmth());
        vo.setFormality(item.getFormality());
        vo.setTags(toStringList(item.getTags()));

        // AI 元信息
        vo.setAiRecognized(item.getAiRecognized());
        vo.setAiModel(item.getAiModel());
        vo.setAiConfidence(item.getAiConfidence());

        // 历史商品信息（淘宝导入已下线，仅老数据有值）
        if (product != null) {
            vo.setNumIid(product.getNumIid());
            vo.setReservePrice(product.getReservePrice());
            vo.setZkFinalPrice(product.getZkFinalPrice());
            vo.setItemUrl(product.getItemUrl());
            vo.setShopTitle(product.getShopTitle());
        }
        return vo;
    }

    // ==================== AI 结果 → PO ====================

    /**
     * 把 AI 识别结果灌入衣柜条目
     *
     * @param modelName 执行识别的模型标识，落库便于回溯
     */
    public void applyRecognizeResult(WardrobeItem item, GarmentRecognizeResult result, String modelName) {
        if (result == null) {
            return;
        }
        item.setTitle(StrUtil.isNotBlank(result.getTitle())
                ? StrUtil.maxLength(result.getTitle(), 500)
                : fallbackTitle(result));
        item.setCategoryL1(result.getCategoryL1());
        item.setCategoryL2(result.getCategoryL2());
        item.setColor(result.getColor());
        item.setColorHex(result.getColorHex());
        item.setColors(toJson(result.getColors()));
        item.setSeasons(joinSeasons(result.getSeasons()));
        item.setStyle(result.getStyle());
        item.setStyles(toJson(result.getStyles()));
        item.setMaterial(result.getMaterial());
        item.setOccasions(toJson(result.getOccasions()));
        item.setFit(result.getFit());
        item.setPattern(result.getPattern());
        item.setThickness(result.getThickness());
        item.setWarmth(result.getWarmth());
        item.setFormality(result.getFormality());
        item.setTags(toJson(result.getTags()));
        item.setAiRecognized(1);
        item.setAiModel(modelName);
        if (result.getConfidence() != null) {
            item.setAiConfidence(BigDecimal.valueOf(result.getConfidence()).setScale(4, RoundingMode.HALF_UP));
        }
        if (StrUtil.isBlank(item.getWearStatus())) {
            item.setWearStatus(WearStatus.WEARING.getValue());
        }
    }

    /**
     * 按「导入任务」构造衣柜条目（AI 导入专用）
     *
     * <p>这是「识别结果 → 条目」的唯一构造入口，被两处复用，避免逻辑漂移：
     * <ol>
     *   <li>{@code WardrobeImportService.toVO} —— 任务未 commit 时<b>临时</b>构造一个条目给前端编辑器当初始值（不落库）</li>
     *   <li>{@code WardrobeImportService.commit} —— 用户点「放入衣橱」时<b>真正</b>构造并落库</li>
     * </ol>
     *
     * <p>之所以能共用：两处对「图片字段怎么填、AI 结果怎么灌、AI 失败怎么降级」的诉求完全一致，
     * 若各写一份，将来加字段必然漏改一边（历史上就出现过「预览有值、入库丢失」这类问题）。
     *
     * @param userId           归属用户
     * @param originObjectKey  原图 objectKey
     * @param cutoutObjectKey  抠图/裁剪产物 objectKey，可为 null（回退原图）
     * @param result           AI 识别结果，可为 null（AI 失败/降级场景）
     * @param modelName        执行识别的模型标识，落库便于回溯
     */
    public WardrobeItem buildImportItem(Long userId, String originObjectKey, String cutoutObjectKey,
                                        GarmentRecognizeResult result, String modelName) {
        WardrobeItem item = new WardrobeItem();
        item.setUserId(userId);
        item.setSource(WardrobeSource.AI_IMPORT.getValue());
        item.setOriginImageKey(originObjectKey);
        item.setCutoutImageKey(cutoutObjectKey);
        // 有抠图/裁剪图用裁剪图作封面，否则回退原图
        item.setImageKey(cutoutObjectKey != null ? cutoutObjectKey : originObjectKey);
        item.setWearStatus(WearStatus.WEARING.getValue());
        item.setWearCount(0);
        item.setAiRecognized(0);

        if (result != null) {
            applyRecognizeResult(item, result, modelName);
            item.setAiRawJson(StrUtil.maxLength(result.getRawJson(), 20000));
        } else {
            // AI 失败/降级：原图 + 未命名衣物 + 未分类，让用户手动补全后仍可入库
            item.setTitle("未命名衣物");
            item.setCategoryL1(GarmentCategoryL1.OTHER.getCode());
            item.setCategoryL2("未分类");
        }

        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        return item;
    }

    /**
     * 用户编辑：非 null 即覆盖
     */
    public void applyUpdate(WardrobeItem item, UpdateWardrobeReq req) {
        if (StrUtil.isNotBlank(req.getTitle())) {
            item.setTitle(req.getTitle().trim());
        }
        if (req.getRemark() != null) {
            item.setRemark(req.getRemark());
        }
        if (StrUtil.isNotBlank(req.getCategoryL1())) {
            GarmentCategoryL1 l1 = GarmentCategoryL1.of(req.getCategoryL1());
            item.setCategoryL1(l1.getCode());
        }
        if (req.getCategoryL2() != null) {
            item.setCategoryL2(req.getCategoryL2().trim());
        }
        if (req.getCategoryId() != null) {
            item.setCategoryId(req.getCategoryId());
        }
        if (req.getColor() != null) {
            item.setColor(req.getColor());
        }
        if (req.getColorHex() != null) {
            item.setColorHex(req.getColorHex());
        }
        if (req.getColors() != null) {
            item.setColors(toJson(req.getColors()));
        }
        if (req.getSeasons() != null) {
            item.setSeasons(joinSeasons(req.getSeasons()));
        }
        if (StrUtil.isNotBlank(req.getWearStatus())) {
            item.setWearStatus(WearStatus.of(req.getWearStatus()).getValue());
        }
        if (req.getBrand() != null) {
            item.setBrand(req.getBrand());
        }
        if (req.getPrice() != null) {
            item.setPrice(req.getPrice());
        }
        if (req.getWearCount() != null) {
            item.setWearCount(Math.max(0, req.getWearCount()));
        }
        if (req.getPurchaseDate() != null) {
            item.setPurchaseDate(req.getPurchaseDate());
        }
        if (req.getStyle() != null) {
            item.setStyle(req.getStyle());
        }
        if (req.getStyles() != null) {
            item.setStyles(toJson(req.getStyles()));
        }
        if (req.getMaterial() != null) {
            item.setMaterial(req.getMaterial());
        }
        if (req.getOccasions() != null) {
            item.setOccasions(toJson(req.getOccasions()));
        }
        if (req.getSize() != null) {
            item.setSize(req.getSize());
        }
        if (req.getFit() != null) {
            item.setFit(req.getFit());
        }
        if (req.getPattern() != null) {
            item.setPattern(req.getPattern());
        }
        if (req.getThickness() != null) {
            item.setThickness(req.getThickness());
        }
        if (req.getWarmth() != null) {
            item.setWarmth(clamp(req.getWarmth(), 1, 5));
        }
        if (req.getFormality() != null) {
            item.setFormality(clamp(req.getFormality(), 1, 5));
        }
        if (req.getTags() != null) {
            item.setTags(toJson(req.getTags()));
        }
        // 单次穿着成本随价格/次数联动
        item.setCostPerWear(calcCostPerWear(item.getPrice(), item.getWearCount()));
        item.setUpdateTime(LocalDateTime.now());
    }

    /**
     * 单次穿着成本 = 价格 / 穿着次数；次数为 0 时不计算（避免除零与无意义展示）
     */
    public BigDecimal calcCostPerWear(BigDecimal price, Integer wearCount) {
        if (price == null || wearCount == null || wearCount <= 0) {
            return null;
        }
        return price.divide(BigDecimal.valueOf(wearCount), 2, RoundingMode.HALF_UP);
    }

    // ==================== 工具 ====================

    public String presign(String objectKey) {
        return StrUtil.isBlank(objectKey) ? null : ossService.generatePresignedUrl(objectKey);
    }

    public List<String> presignList(List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> urls = new ArrayList<>();
        for (String key : objectKeys) {
            String url = presign(key);
            if (url != null) {
                urls.add(url);
            }
        }
        return urls;
    }

    /**
     * JSON 数组字符串 → List；容错兼容历史「逗号分隔」写法
     */
    public List<String> toStringList(String json) {
        if (StrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        String text = json.trim();
        try {
            if (text.startsWith("[")) {
                List<String> list = JSONUtil.toList(text, String.class);
                return list == null ? Collections.emptyList() : list;
            }
        } catch (Exception e) {
            log.warn("[WardrobeConverter] JSON数组解析失败，降级按逗号切分: {}", text);
        }
        return new ArrayList<>(Arrays.asList(text.split("\\s*,\\s*")));
    }

    public String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        List<String> cleaned = new ArrayList<>();
        for (String item : list) {
            if (StrUtil.isNotBlank(item)) {
                cleaned.add(item.trim());
            }
        }
        return cleaned.isEmpty() ? null : JSONUtil.toJsonStr(cleaned);
    }

    /** 季节码列表 → CSV，固定 春夏秋冬 顺序 */
    private String joinSeasons(List<String> seasons) {
        if (seasons == null || seasons.isEmpty()) {
            return null;
        }
        List<String> ordered = new ArrayList<>();
        for (String code : SEASON_NAMES.keySet()) {
            for (String item : seasons) {
                if (item != null && item.trim().toUpperCase().equals(code)) {
                    ordered.add(code);
                    break;
                }
            }
        }
        return ordered.isEmpty() ? null : String.join(",", ordered);
    }

    /** CSV → 季节码列表，过滤非法值 */
    private List<String> parseSeasons(String csv) {
        if (StrUtil.isBlank(csv)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String code : SEASON_NAMES.keySet()) {
            for (String item : csv.split(",")) {
                if (item != null && item.trim().toUpperCase().equals(code)) {
                    result.add(code);
                    break;
                }
            }
        }
        return result;
    }

    private String categoryL1Name(String code) {
        if (StrUtil.isBlank(code)) {
            return null;
        }
        return GarmentCategoryL1.of(code).getDesc();
    }

    private String fullCategoryName(String l1, String l2) {
        if (StrUtil.isBlank(l1)) {
            return StrUtil.isBlank(l2) ? null : l2;
        }
        String l1Name = GarmentCategoryL1.of(l1).getDesc();
        return StrUtil.isBlank(l2) ? l1Name : l1Name + "-" + l2;
    }

    private String fallbackTitle(GarmentRecognizeResult result) {
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(result.getColor())) {
            sb.append(result.getColor());
        }
        if (StrUtil.isNotBlank(result.getCategoryL2())) {
            sb.append(result.getCategoryL2());
        }
        return sb.length() == 0 ? "未命名衣物" : StrUtil.maxLength(sb.toString(), 500);
    }

    private Integer clamp(Integer value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
