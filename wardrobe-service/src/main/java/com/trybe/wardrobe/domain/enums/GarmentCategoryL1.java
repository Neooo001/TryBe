package com.trybe.wardrobe.domain.enums;

import com.trybe.common.exception.BadRequestException;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 衣服一级分类枚举
 *
 * <p>与前端衣橱页分类 Tab、AI 识别输出严格对齐。
 * 二级分类名称不在此枚举内（由 wardrobe_category 表按用户维护），
 * 但提供 {@link #defaultChildren()} 作为首次初始化与 AI 结果校验的默认模板。
 */
@Getter
public enum GarmentCategoryL1 {

    TOP("TOP", "上装", Arrays.asList("外套", "短袖", "卫衣", "毛衣", "背心", "打底")),
    BOTTOM("BOTTOM", "下装", Arrays.asList("长裤", "短裤", "长裙", "短裙", "打底裤")),
    SET("SET", "套装", Arrays.asList("连衣裙", "连裤")),
    SHOES("SHOES", "鞋子", Arrays.asList("运动鞋", "休闲鞋", "凉鞋", "拖鞋", "高跟鞋", "靴子", "皮鞋")),
    BAG("BAG", "包包", Arrays.asList("单肩包", "双肩包", "手拿包")),
    ACCESSORY("ACCESSORY", "首饰", Arrays.asList("项链", "耳环", "手链", "戒指", "发饰")),
    DECOR("DECOR", "配饰", Arrays.asList("帽子", "围巾", "墨镜", "腰带", "手表", "袜子", "丝巾")),
    OTHER("OTHER", "未分类", Arrays.asList("未分类")),
    ;

    /** 业务码 */
    private final String code;
    /** 中文名 */
    private final String desc;
    /** 默认二级分类模板 */
    private final List<String> defaultChildren;

    GarmentCategoryL1(String code, String desc, List<String> defaultChildren) {
        this.code = code;
        this.desc = desc;
        this.defaultChildren = defaultChildren;
    }

    /**
     * 宽松解析：兼容 AI 返回的 code / 中文名 / 大小写差异；无法识别时归入 OTHER
     */
    public static GarmentCategoryL1 of(String value) {
        if (value == null || value.trim().isEmpty()) {
            return OTHER;
        }
        String v = value.trim();
        for (GarmentCategoryL1 item : values()) {
            if (item.code.equalsIgnoreCase(v) || item.desc.equals(v)) {
                return item;
            }
        }
        // 常见同义词兜底，降低 AI 输出偏差带来的归类错误
        String lower = v.toLowerCase();
        if (lower.contains("裤") || lower.contains("裙") || lower.contains("bottom")) {
            return BOTTOM;
        }
        if (lower.contains("鞋") || lower.contains("shoe")) {
            return SHOES;
        }
        if (lower.contains("包") || lower.contains("bag")) {
            return BAG;
        }
        if (lower.contains("项链") || lower.contains("耳") || lower.contains("戒指") || lower.contains("首饰")) {
            return ACCESSORY;
        }
        if (lower.contains("帽") || lower.contains("围巾") || lower.contains("腰带") || lower.contains("袜")) {
            return DECOR;
        }
        if (lower.contains("外套") || lower.contains("衣") || lower.contains("top")) {
            return TOP;
        }
        return OTHER;
    }

    /**
     * 严格解析：非法值直接抛异常，用于接口入参校验
     */
    public static GarmentCategoryL1 strictOf(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new BadRequestException("分类不能为空");
        }
        for (GarmentCategoryL1 item : values()) {
            if (item.code.equalsIgnoreCase(value.trim())) {
                return item;
            }
        }
        throw new BadRequestException("无效的分类: " + value);
    }

    /** 全部一级分类中文名，用于 AI 提示词与文档 */
    public static List<String> allDescs() {
        return Arrays.stream(values()).map(GarmentCategoryL1::getDesc).collect(Collectors.toList());
    }
}
