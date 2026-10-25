package com.trybe.api.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 「我的作品」卡片视图（小程序「我的」页面 → 我的作品）
 *
 * <p><b>这是创作者本人的视图，但只开了「看 + 开关」两件事</b>：
 * 小程序端不做编辑，所以这里只给渲染列表必需的内容字段，外加两个按钮的显隐标记。
 *
 * <p>与浏览者视图 {@link WorkBriefDTO} 的关键差别：
 * <ul>
 *   <li>带 {@code status} —— 本人看得到自己作品是「已上架」还是「已下架」</li>
 *   <li>带 {@code canOnShelf / canOffShelf} —— 状态机能怎么流转由后端算好，
 *       前端只负责按布尔值显示按钮，不重复实现一遍状态机（避免两边规则漂移）</li>
 *   <li>不带创作者信息 —— 自己看自己的作品，昵称头像没有意义</li>
 * </ul>
 *
 * <p>依然<b>不返回</b>：任何 {@code ai_} 前缀的 AI 解析结果、价格、外链、素材 key。
 * 图片一律给服务端现算的预签名 URL。
 */
@Data
@ApiModel(description = "我的作品卡片")
public class MyWorkBriefDTO {

    @ApiModelProperty("作品ID")
    private Long id;

    @ApiModelProperty("作品业务编号")
    private String itemNo;

    @ApiModelProperty("作品标题")
    private String title;

    @ApiModelProperty("作品描述（可为空）")
    private String description;

    @ApiModelProperty("状态：ON_SHELF已上架 / OFF_SHELF已下架")
    private String status;

    @ApiModelProperty("状态中文文案：已上架 / 已下架")
    private String statusDesc;

    @ApiModelProperty("作品分区，如 上衣/连衣裙")
    private String zone;

    @ApiModelProperty("内容标签")
    private List<String> tags;

    @ApiModelProperty("封面图预签名访问 URL（null=前端渲染占位图）")
    private String imageUrl;

    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty("是否可上架（true 时前端显示「上架」按钮）")
    private Boolean canOnShelf;

    @ApiModelProperty("是否可下架（true 时前端显示「下架」按钮）")
    private Boolean canOffShelf;
}
