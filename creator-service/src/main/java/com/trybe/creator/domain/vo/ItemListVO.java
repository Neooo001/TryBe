package com.trybe.creator.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作品列表视图对象（I1 出参）
 *
 * <p><b>这是创作者视图，不是 creator_item 的镜像。</b>只放创作者能看到的东西：
 * 自己发布的标题/描述/分区/标签 + 状态与封面。
 *
 * <p>刻意<b>不返回</b>分类、价格、AI 回填的商品信息：那些是平台侧推广数据，
 * 创作者既不能管也不该看到（详情接口同理，见 {@link ItemDetailVO}）。
 *
 * <p>也没有逐篇互动指标（播放/点赞/评论/试穿）—— creator-service 目前只提供
 * 创作者维度的总览统计（{@code /creator/stats/overview}），没有逐篇口径，
 * 所以列表页不展示这部分，避免真实作品配上编造的数字。
 */
@Data
@ApiModel(description = "作品列表信息（创作者视图）")
public class ItemListVO {

    @ApiModelProperty("作品ID")
    private Long id;

    @ApiModelProperty("作品业务编号")
    private String itemNo;

    @ApiModelProperty("标题")
    private String title;

    @ApiModelProperty("描述")
    private String description;

    @ApiModelProperty("状态：DRAFT草稿/OFF_SHELF已下架/ON_SHELF已上架")
    private String status;

    @ApiModelProperty("作品分区")
    private String zone;

    @ApiModelProperty("作品标签（JSON数组字符串，创作者自己填的）")
    private String patternTags;

    @ApiModelProperty("主图预签名访问 URL")
    private String imageUrl;

    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;
}
