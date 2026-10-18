package com.trybe.api.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * C 端作品卡片视图（小程序主页瀑布流 / 圈子信息流）
 *
 * <p><b>这是浏览者视图，不是 creator_item 的镜像。</b>只包含创作者公开发布的内容字段：
 * 封面图 / 标题 / 描述 / 分区 / 标签 / 季节 / 款式 + 创作者的公开主页信息（昵称、头像）。
 *
 * <p>刻意<b>不返回</b>：
 * <ul>
 *   <li>任何 {@code ai_} 前缀的 AI 解析结果（平台侧数据，对创作者都不可见，对 C 端更不公开）</li>
 *   <li>价格 {@code price}、外链 {@code linkUrl}（本轮卡片只做内容展示，不做电商转化）</li>
 *   <li>状态 {@code status}（能出现在 C 端的必然是 ON_SHELF，无需外泄状态机）</li>
 * </ul>
 *
 * <p>图片一律给「服务端现算的预签名 URL」而不是 OSS key：小程序端不做签名，
 * 也拿不到 OSS 凭据；key 是持久化事实，URL 每次现算。
 */
@Data
@ApiModel(description = "C 端作品卡片")
public class WorkBriefDTO {

    @ApiModelProperty("作品ID")
    private Long id;

    @ApiModelProperty("作品业务编号")
    private String itemNo;

    @ApiModelProperty("作品标题")
    private String title;

    @ApiModelProperty("作品描述（可为空）")
    private String description;

    @ApiModelProperty("作品分区，如 上衣/连衣裙/发色")
    private String zone;

    @ApiModelProperty("封面图预签名访问 URL（null=前端渲染占位图）")
    private String imageUrl;

    @ApiModelProperty("内容标签")
    private List<String> tags;

    @ApiModelProperty("适用季节")
    private List<String> seasons;

    @ApiModelProperty("衣服款式")
    private List<String> styles;

    @ApiModelProperty("创作者ID（web 域 web_user.id，仅用于展示归属，不用于任何鉴权）")
    private Long creatorId;

    @ApiModelProperty("创作者昵称（无档案时为默认兜底名）")
    private String creatorNickname;

    @ApiModelProperty("创作者头像预签名访问 URL（null=前端渲染占位头像）")
    private String creatorAvatarUrl;

    @ApiModelProperty("发布时间")
    private LocalDateTime publishTime;
}
