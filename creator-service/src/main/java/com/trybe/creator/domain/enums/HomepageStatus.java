package com.trybe.creator.domain.enums;

import lombok.Getter;

/**
 * 创作者主页展示状态
 *
 * <p><b>权限约定</b>：该状态只有平台管理端可修改（v1 暂无管理端接口，默认 ENABLED）；
 * 创作者个人中心接口（GET/PUT /creator/profile）对它<b>只读</b>，
 * 更新白名单不含此字段，防止越权自助停用/启用绕过平台治理。
 */
@Getter
public enum HomepageStatus {

    /** 启用：公开主页对外可见 */
    ENABLED("ENABLED", "已启用"),
    /** 停用：公开主页对外隐藏（仅管理端可操作） */
    DISABLED("DISABLED", "已停用"),
    ;

    private final String code;
    private final String desc;

    HomepageStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 宽松解析：未知值返回 null（历史脏数据兜底，展示层按原文显示）
     */
    public static HomepageStatus of(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        for (HomepageStatus status : values()) {
            if (status.code.equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        return null;
    }
}
