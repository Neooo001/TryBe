package com.trybe.creator.domain.enums;

import com.trybe.common.exception.BadRequestException;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 创作者创作方向字典（个人中心「擅长领域/创作方向」可选项）
 *
 * <p>字典由后端统一维护，接口返回全部可选项（{@code GET /creator/profile} 的
 * {@code fieldOptions}），<b>前端不得硬编码选项列表</b>；创作者已选项存
 * {@code creator_profile.field_codes}（逗号分隔 code，最多 {@link #MAX_SELECTED} 个），
 * 存储格式沿用 {@code creator_application.category_scope} 的先例。
 *
 * <p>新增方向：只在本地加枚举值即可，无需改表结构（VARCHAR 逗号分隔天然可扩展）。
 */
@Getter
public enum CreatorField {

    /** 发色/染发 */
    HAIR_COLOR("HAIR_COLOR", "发色"),
    /** 穿搭搭配 */
    OUTFIT("OUTFIT", "穿搭"),
    /** 美甲 */
    NAIL_ART("NAIL_ART", "美甲"),
    /** 美妆 */
    MAKEUP("MAKEUP", "美妆"),
    /** 配饰 */
    ACCESSORY("ACCESSORY", "配饰"),
    /** 鞋包 */
    SHOES_BAG("SHOES_BAG", "鞋包"),
    ;

    /** 创作者最多可选的创作方向数量 */
    public static final int MAX_SELECTED = 5;

    private final String code;
    private final String desc;

    CreatorField(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 严格解析：非法编码直接抛参数错误（100）
     */
    public static CreatorField strictOf(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new BadRequestException("创作方向编码不能为空");
        }
        for (CreatorField field : values()) {
            if (field.code.equalsIgnoreCase(code.trim())) {
                return field;
            }
        }
        throw new BadRequestException("无效的创作方向: " + code);
    }

    /**
     * 全部可选项（字典输出，按枚举声明顺序即推荐排序）
     */
    public static List<CreatorField> all() {
        return Arrays.asList(values());
    }

    /**
     * 全部合法 code 集合（校验用）
     */
    public static List<String> allCodes() {
        return all().stream().map(CreatorField::getCode).collect(Collectors.toList());
    }
}
