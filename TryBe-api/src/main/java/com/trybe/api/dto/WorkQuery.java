package com.trybe.api.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Min;

/**
 * C 端作品查询条件（小程序主页 / 圈子浏览）
 *
 * <p><b>刻意不继承 {@code PageQuery}</b>：{@code PageQuery} 的分页方法签名引用了
 * MyBatis-Plus 的 {@code Page} 类，而 TryBe-api 是纯 Feign 契约模块（不引持久层依赖），
 * 继承它会让编译本模块时 javac 去加载 {@code Page} 而失败。分页字段在这里自带，
 * 由数据出口侧（creator-service）转成 {@code PageQuery} 后再用。
 *
 * <p>分页参数名沿用 {@code pageNo} / {@code pageSize}（不是 page/size）。
 *
 * <p>筛选与检索只落在「创作者公开发布时自己填的列」上（标题 / 描述 / 标签 / 款式 / 分区），
 * <b>绝不碰任何 {@code ai_} 列</b> —— 否则可以用关键词二分试探把 AI 解析结果反推出来。
 *
 * <p>本类同时是 Feign 的查询对象（{@code @SpringQueryMap}），属性保持基础类型 / String。
 */
@Data
@ApiModel(description = "C 端作品查询条件")
public class WorkQuery {

    public static final int DEFAULT_PAGE_NO = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 单页上限：C 端接口匿名可访，必须封顶，防止 pageSize 被放大拖垮数据库 */
    public static final int MAX_PAGE_SIZE = 50;

    /** 关键词最大长度 */
    public static final int MAX_KEYWORD_LENGTH = 50;

    @ApiModelProperty("页码，从 1 开始")
    @Min(value = 1, message = "页码不能小于1")
    private Integer pageNo = DEFAULT_PAGE_NO;

    @ApiModelProperty("每页数量")
    @Min(value = 1, message = "每页数量不能小于1")
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    @ApiModelProperty("作品分区过滤，取值见 /community/works/zones（可空=全部）")
    private String zone;

    @ApiModelProperty("关键词：模糊匹配标题 / 描述 / 标签 / 款式（可空）")
    private String keyword;

    /**
     * 入参归一化：分页兜底 + 长度封顶 + 空白视为空
     *
     * <p>调用方（content-service）与数据出口（creator-service）都过一遍，
     * 保证「匿名可访的 C 端接口」不会被异常分页或超长关键词拖垮。
     *
     * @return this（链式）
     */
    public WorkQuery normalize() {
        if (pageNo == null || pageNo < 1) {
            pageNo = DEFAULT_PAGE_NO;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = DEFAULT_PAGE_SIZE;
        } else if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }
        this.zone = blankToNull(this.zone);
        this.keyword = blankToNull(this.keyword);
        if (keyword != null && keyword.length() > MAX_KEYWORD_LENGTH) {
            keyword = keyword.substring(0, MAX_KEYWORD_LENGTH);
        }
        return this;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
