package com.trybe.wardrobe.domain.enums;

import com.trybe.common.exception.BadRequestException;
import lombok.Getter;

/**
 * 衣柜条目来源枚举
 */
@Getter
public enum WardrobeSource {
    AI_IMPORT("AI_IMPORT", "AI识别导入"),
    /**
     * 历史遗留：淘宝链接导入功能已下线（个人开发者无法开通淘宝客 API）。
     *
     * <p>枚举值刻意保留 —— 库里可能还有 source=TAOBAO_IMPORT 的老数据，
     * 删掉会导致这些记录在查询/展示时报「无效的衣柜来源」。**新代码不要再产生这个值**。
     */
    @Deprecated
    TAOBAO_IMPORT("TAOBAO_IMPORT", "淘宝导入"),
    MANUAL("MANUAL", "手动添加"),
    CUSTOM_OUTFIT("CUSTOM_OUTFIT", "自定义穿搭"),
    ;

    private final String value;
    private final String desc;

    WardrobeSource(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static WardrobeSource of(String value) {
        for (WardrobeSource source : values()) {
            if (source.value.equals(value)) {
                return source;
            }
        }
        throw new BadRequestException("无效的衣柜来源: " + value);
    }

    /**
     * 安全取中文名，未知来源返回原值，供 VO 展示使用（不抛异常）
     */
    public static String descOf(String value) {
        if (value == null) {
            return null;
        }
        for (WardrobeSource source : values()) {
            if (source.value.equals(value)) {
                return source.desc;
            }
        }
        return value;
    }
}
