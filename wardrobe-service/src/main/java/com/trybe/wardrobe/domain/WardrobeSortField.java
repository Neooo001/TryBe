package com.trybe.wardrobe.domain;

import lombok.Getter;

/**
 * 衣柜可排序字段白名单。
 *
 * <p>这是「排序字段收敛」的唯一事实来源：只有这里登记过的列名才可能进入 ORDER BY，
 * 前端传来的一切排序参数都要先在这一层被翻译成 {@link #column}，从而杜绝任意列名 / SQL 片段注入。
 *
 * <p>三个属性各自的作用：
 * <ul>
 *   <li>{@code column}   —— 与 DDL 完全一致的物理列名（大小写、下划线严格对齐，错一个排序就静默失效）；</li>
 *   <li>{@code nullable} —— 该列是否可能为 {@code NULL}。为 {@code true} 时排序需追加
 *       {@code (col IS NULL)} 前置项实现「空值沉底」，为 {@code false} 时列本身非空、无需追加；</li>
 *   <li>{@code defaultAsc} —— 未显式给方向或方向非法时的默认方向。</li>
 * </ul>
 *
 * <p>列可空性依据 {@code sql/wardrobeService/*.sql} 的 DDL：
 * create_time / update_time 为 {@code NOT NULL}；wear_count 为 {@code NOT NULL DEFAULT 0}；
 * price / cost_per_wear / purchase_date 为 {@code DEFAULT NULL}（可空）。
 *
 * <p>注意：{@code price = 0}、{@code wear_count = 0} 都是**有效值**，不属于「空」。
 */
@Getter
public enum WardrobeSortField {

    CREATE_TIME("create_time", "添加时间", false, false),
    UPDATE_TIME("update_time", "最近更新", false, false),
    PRICE("price", "价格", true, false),
    WEAR_COUNT("wear_count", "穿着次数", false, false),
    // 单次穿着成本「越低越划算」，降序没有业务意义，故默认升序
    COST_PER_WEAR("cost_per_wear", "单次穿着成本", true, true),
    PURCHASE_DATE("purchase_date", "购买日期", true, false),
    ;

    private final String column;
    private final String label;
    private final boolean nullable;
    private final boolean defaultAsc;

    WardrobeSortField(String column, String label, boolean nullable, boolean defaultAsc) {
        this.column = column;
        this.label = label;
        this.nullable = nullable;
        this.defaultAsc = defaultAsc;
    }

    /**
     * 按列名宽松解析：大小写不敏感、忽略首尾空白。
     *
     * <p>未命中返回 {@code null}（**不抛异常**）—— 排序参数脏不应让接口失败，
     * 由调用方决定「丢弃该段」或「回落默认排序」。
     *
     * @param token 待解析的字段名（如 {@code "price"}、{@code " PRICE "}）
     * @return 命中的枚举；未命中返回 {@code null}
     */
    public static WardrobeSortField of(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        for (WardrobeSortField item : values()) {
            if (item.column.equalsIgnoreCase(normalized)) {
                return item;
            }
        }
        return null;
    }
}
