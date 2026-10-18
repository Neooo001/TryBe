package com.trybe.creator.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 作品详情视图对象（I2 / I3 / I4 出参）
 *
 * <p><b>这是创作者视图，不是 creator_item 的镜像。</b>字段集刻意与
 * {@code ItemSaveReq} 对齐：能被写进来的，才是能被看到的。
 *
 * <p>刻意<b>不返回</b>（判据就一条：<b>凡 {@code ai_} 前缀的列，一律不出现在这里</b>）：
 * <ul>
 *   <li>AI 解析产出的全部内容（商品名 / 分类 / 主色 / 季节 / 款式 / 材质 / 卖点 / 建议售价）——
 *       这些与创作者手填的 seasons / styles / material 是两份独立数据。
 *       只保留 {@code aiParsed} 这一个「生成中 / 已生成」状态位，
 *       让界面能给出流程反馈，但拿不到任何解析内容；</li>
 *   <li>试穿素材与尺寸标定（透明底图/参考图/实物宽高/像素标定/锚点）—— 本轮不开放。</li>
 * </ul>
 *
 * <p>注意 {@code seasons} / {@code styles} / {@code material} 在这里是<b>创作者自己填的</b>那份，
 * 不是 AI 解析结果。
 */
@Data
@ApiModel(description = "作品详情信息（创作者视图）")
public class ItemDetailVO {

    @ApiModelProperty("作品ID")
    private Long id;

    @ApiModelProperty("作品业务编号")
    private String itemNo;

    @ApiModelProperty("创作者用户ID")
    private Long creatorId;

    @ApiModelProperty("标题")
    private String title;

    @ApiModelProperty("描述")
    private String description;

    @ApiModelProperty("作品外链（购买链接/同款链接）")
    private String linkUrl;

    @ApiModelProperty("状态：DRAFT草稿/OFF_SHELF已下架/ON_SHELF已上架")
    private String status;

    @ApiModelProperty("试穿模式 FLAT/MODEL/BOTH（发布时固定，不可编辑）")
    private String tryonMode;

    @ApiModelProperty("作品分区")
    private String zone;

    @ApiModelProperty("作品标签（JSON数组字符串，创作者自己填的）")
    private String patternTags;

    @ApiModelProperty("适用季节（JSON数组字符串，创作者自己填的；AI 判定结果不在此字段）")
    private String seasons;

    @ApiModelProperty("衣服款式（JSON数组字符串，创作者自己填的；AI 判定结果不在此字段）")
    private String styles;

    @ApiModelProperty("材质成分（创作者自己填的；AI 判定结果不在此字段）")
    private String material;

    @ApiModelProperty("作品话题（JSON数组字符串）")
    private String topics;

    @ApiModelProperty("当前主图 OSS objectKey")
    private String imageKey;

    @ApiModelProperty("当前主图预签名访问 URL")
    private String imageUrl;

    @ApiModelProperty("商品信息生成状态：0 生成中 / 1 已生成（仅状态，不含内容）")
    private Integer aiParsed;

    @ApiModelProperty("作品图片列表（可增删；换主图就是从其中挑一张）")
    private List<ModelImageVO> modelImages;

    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty("更新时间")
    private LocalDateTime updateTime;
}
