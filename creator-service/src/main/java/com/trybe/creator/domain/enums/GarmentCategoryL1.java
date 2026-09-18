package com.trybe.creator.domain.enums;

import com.trybe.common.exception.BadRequestException;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 物品一级分类（从 wardrobe-service 复制一份，字符串常量对齐）
 *
 * <p>creator_item.category_code 存储该 code，创作者入驻的经营类目（category_scope）
 * 同样使用逗号分隔的 code 列表。
 */
@Getter
public enum GarmentCategoryL1 {

    TOP("TOP", "上装"),
    BOTTOM("BOTTOM", "下装"),
    SET("SET", "套装"),
    SHOES("SHOES", "鞋子"),
    BAG("BAG", "包包"),
    ACCESSORY("ACCESSORY", "首饰"),
    DECOR("DECOR", "配饰"),
    OTHER("OTHER", "未分类"),
    ;

    private final String code;
    private final String desc;

    GarmentCategoryL1(String code, String desc) {
        this.code = code;
        this.desc = desc;
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

    /**
     * 宽松解析：无法识别时返回 null（用于审核列表等展示场景，不因脏数据整体失败）
     */
    public static GarmentCategoryL1 of(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(c -> c.code.equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElse(null);
    }

    /** 全部一级分类 code，用于 AI 提示词与文档 */
    public static List<String> allCodes() {
        return Arrays.stream(values()).map(GarmentCategoryL1::getCode).collect(Collectors.toList());
    }
}
