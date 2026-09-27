package com.trybe.creator.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创作者个人中心资料 VO（GET / PUT /creator/profile）
 *
 * <p>聚合三段信息：公开主页资料（可编辑）+ 账号资料（只读投影）+ 创作方向字典（可选项）。
 * 图片只落 OSS objectKey，访问 URL 由服务端按现有文件访问策略生成预签名 URL，
 * <b>不含长期签名 URL 落库语义</b>（key 才是持久化事实，URL 每次现算）。
 */
@Data
@ApiModel(description = "创作者个人中心资料")
public class CreatorProfileVO {

    @ApiModelProperty("创作者用户ID（web域 web_user.id）")
    private Long creatorId;

    // ==================== 公开主页资料（可编辑） ====================

    @ApiModelProperty("创作者昵称（公开主页展示名）")
    private String nickname;

    @ApiModelProperty("头像 OSS objectKey（null=使用默认头像）")
    private String avatarKey;

    @ApiModelProperty("头像预签名访问 URL（null=使用默认头像，由前端渲染占位图）")
    private String avatarUrl;

    @ApiModelProperty("主页封面图 OSS objectKey（null=使用默认封面）")
    private String coverKey;

    @ApiModelProperty("主页封面图预签名访问 URL（null=使用默认封面）")
    private String coverUrl;

    @ApiModelProperty("个人简介（可为空）")
    private String bio;

    @ApiModelProperty("已选创作方向编码列表（如 HAIR_COLOR,OUTFIT）")
    private List<String> fieldCodes;

    @ApiModelProperty("主页展示状态 ENABLED已启用/DISABLED已停用（只读，仅平台管理端可修改）")
    private String homepageStatus;

    // ==================== 账号资料（只读） ====================

    @ApiModelProperty("账号资料（web_user 只读投影，不含密码哈希）")
    private AccountVO account;

    // ==================== 创作方向字典（可选项） ====================

    @ApiModelProperty("可选创作方向列表（字典由后端维护，前端不得硬编码）")
    private List<FieldOptionVO> fieldOptions;

    @ApiModelProperty("创作方向最多可选数量（随字典下发，避免前端硬编码同一约束两处维护）")
    private int fieldMax;

    /**
     * 账号资料段（web_user 只读投影）
     */
    @Data
    @ApiModel(description = "创作者账号资料（只读）")
    public static class AccountVO {

        @ApiModelProperty("登录手机号（脱敏展示，如 138****8000；web域账号标识，不可修改）")
        private String phoneMasked;

        @ApiModelProperty("账号昵称（注册昵称，与公开主页昵称相互独立，只读）")
        private String nickname;

        @ApiModelProperty("账号创建时间")
        private LocalDateTime createTime;

        @ApiModelProperty("最近登录时间")
        private LocalDateTime lastLoginTime;
    }

    /**
     * 创作方向可选项
     */
    @Data
    @ApiModel(description = "创作方向可选项")
    public static class FieldOptionVO {

        @ApiModelProperty("编码，如 HAIR_COLOR")
        private String code;

        @ApiModelProperty("名称，如 发色")
        private String name;

        public FieldOptionVO() {
        }

        public FieldOptionVO(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }
}
