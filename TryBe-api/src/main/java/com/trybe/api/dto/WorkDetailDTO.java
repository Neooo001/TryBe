package com.trybe.api.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * C 端作品详情视图（小程序作品详情页）
 *
 * <p><b>这是浏览者视图，不是 creator_item 的镜像。</b>它与 {@link WorkBriefDTO} 同源同纪律，
 * 只是把列表卡片里「看不全」的那半截补上：正文描述、多图、话题、材质与创作者简介。
 *
 * <p>刻意<b>不返回</b>（与卡片完全一致的排除清单，避免详情页成为绕过卡片字段裁剪的后门）：
 * <ul>
 *   <li>任何 {@code ai_} 前缀的 AI 解析结果；</li>
 *   <li>价格 {@code price} 与外链 {@code linkUrl}（详情页本轮不做电商转化）；</li>
 *   <li>作品状态 {@code status}（能出现在 C 端的必然是 ON_SHELF）；</li>
 *   <li>互动数（点赞 / 收藏 / 评论）—— 这些属于互动域，作品域不持有也不应编造。</li>
 * </ul>
 *
 * <p>图片给的是「服务端现算的预签名 URL」：小程序端不做签名，也拿不到 OSS 凭据。
 *
 * @author TryBe Team
 */
@Data
@ApiModel(description = "C 端作品详情")
public class WorkDetailDTO {

    @ApiModelProperty("作品ID")
    private Long id;

    @ApiModelProperty("作品业务编号")
    private String itemNo;

    @ApiModelProperty("作品标题")
    private String title;

    @ApiModelProperty("作品描述（正文，可为空）")
    private String description;

    @ApiModelProperty("作品分区，如 上衣/连衣裙/发色")
    private String zone;

    @ApiModelProperty("封面图预签名 URL（null=前端渲染占位图）")
    private String imageUrl;

    @ApiModelProperty("作品图片列表（预签名 URL，已按 sortOrder 排序，封面在首位）")
    private List<String> images;

    @ApiModelProperty("内容标签")
    private List<String> tags;

    @ApiModelProperty("适用季节")
    private List<String> seasons;

    @ApiModelProperty("衣服款式")
    private List<String> styles;

    @ApiModelProperty("作品话题")
    private List<String> topics;

    @ApiModelProperty("材质成分，如 棉95%/氨纶5%")
    private String material;

    @ApiModelProperty("创作者ID（app_user.id，仅用于展示归属，不用于任何鉴权）")
    private Long creatorId;

    @ApiModelProperty("创作者昵称（无档案时为默认兜底名）")
    private String creatorNickname;

    @ApiModelProperty("创作者头像预签名 URL（null=前端渲染占位头像）")
    private String creatorAvatarUrl;

    @ApiModelProperty("创作者简介（可为空）")
    private String creatorBio;

    @ApiModelProperty("发布时间")
    private LocalDateTime publishTime;
}
