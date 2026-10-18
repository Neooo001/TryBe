package com.trybe.common.ai;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 服装一级分类共享字典（AI 识别链路的唯一口径）
 *
 * <p>用途只有两个：
 * <ol>
 *   <li>给视觉大模型的提示词注入分类字典，约束 <b>categoryL1</b> 的取值范围，降低自由发挥导致的归类漂移；</li>
 *   <li>把模型返回的 code 归一化（兼容大小写与中文名），并给出中文名用于展示。</li>
 * </ol>
 *
 * <p><b>与各业务服务的枚举关系</b>：wardrobe-service / creator-service 各自维护自己的
 * {@code GarmentCategoryL1}（那边还带「默认二级分类模板」等业务语义，不属识别链路）。
 * 双方以本枚举的 {@code code} 为共同契约，<b>改动 code 取值必须同步所有使用方</b>。
 */
@Getter
public enum GarmentCategory {

    TOP("TOP", "上装"),
    BOTTOM("BOTTOM", "下装"),
    SET("SET", "套装"),
    SHOES("SHOES", "鞋子"),
    BAG("BAG", "包包"),
    ACCESSORY("ACCESSORY", "首饰"),
    DECOR("DECOR", "配饰"),
    OTHER("OTHER", "未分类"),
    ;

    /** 业务码 */
    private final String code;
    /** 中文名 */
    private final String desc;

    GarmentCategory(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 宽松解析：兼容 code / 中文名 / 大小写差异；无法识别时归入 {@link #OTHER}，绝不抛异常
     */
    public static GarmentCategory of(String value) {
        if (value == null || value.trim().isEmpty()) {
            return OTHER;
        }
        String v = value.trim();
        for (GarmentCategory item : values()) {
            if (item.code.equalsIgnoreCase(v) || item.desc.equals(v)) {
                return item;
            }
        }
        return OTHER;
    }

    /** 分类码的中文名，未知码回落「未分类」 */
    public static String descOf(String code) {
        return of(code).getDesc();
    }

    /** 全部一级分类说明，用于拼装提示词 */
    public static String promptDict() {
        return Arrays.stream(values())
                .map(item -> item.code + "=" + item.desc)
                .collect(Collectors.joining(", "));
    }

    /** 全部一级分类码 */
    public static List<String> allCodes() {
        return Arrays.stream(values()).map(GarmentCategory::getCode).collect(Collectors.toList());
    }
}
