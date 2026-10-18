package com.trybe.creator.domain.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 作品分区字典（{@code creator_item.zone} 的取值域，与创作者后台发布页 WORK_ZONES 对齐）
 *
 * <p>存储值就是中文名（历史数据如此，不改造存量列），所以 {@link #getCode()} 为中文，
 * 枚举常量名只是 Java 侧的英文标识。改这个字典 = 改发布页选项 + C 端筛选，
 * 两边都从本枚举取，避免「前端硬编码一份、后端查库一份」的漂移。
 *
 * <p>C 端通过 {@code GET /community/works/zones} 拿到本字典，顺序即展示顺序。
 */
@Getter
public enum WorkZone {

    UPPER("上衣"),
    LOWER("下衣"),
    DRESS("连衣裙"),
    OUTERWEAR("外套"),
    SHOES("鞋靴"),
    BAG_ACCESSORY("包袋配饰"),
    HAIR_COLOR("发色"),
    COLOR_CONTACT_LENS("美瞳"),
    ;

    private final String code;

    WorkZone(String code) {
        this.code = code;
    }

    /**
     * 宽松解析：无法识别返回 null（用于筛选场景的入参校验，不抛异常打断浏览）
     */
    public static WorkZone of(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String target = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(z -> z.code.toLowerCase(Locale.ROOT).equals(target))
                .findFirst()
                .orElse(null);
    }

    /**
     * 是否为合法分区值
     */
    public static boolean isValid(String value) {
        return of(value) != null;
    }

    /**
     * 字典全量（按声明顺序）
     */
    public static List<String> codes() {
        return Arrays.stream(values()).map(WorkZone::getCode).collect(Collectors.toList());
    }
}
