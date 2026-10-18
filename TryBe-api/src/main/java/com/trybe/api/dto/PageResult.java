package com.trybe.api.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 契约层分页结构（服务间 / 前端共用）
 *
 * <p><b>为什么不用 common 的 {@code PageDTO}</b>：{@code PageDTO} 的静态方法签名引用了
 * MyBatis-Plus 的 {@code Page} 类。不引持久层依赖的服务（content-service）在反序列化它时，
 * Jackson 调 {@code getDeclaredMethods()} 会抛 {@code NoClassDefFoundError: Page}，
 * 于是 canDeserialize 返回 false，最终报一个极具误导性的
 * 「no suitable HttpMessageConverter found for response type ... content type application/json」。
 * 序列化方向同理 —— 对外返回 {@code PageDTO} 时也会炸。
 *
 * <p>契约层（本模块 <b>TryBe-api</b>）不引 MyBatis-Plus，因此这里自带一份字段完全相同的
 * 分页结构：{@code total / pages / list}，前端与服务间调用方都无需感知差别。
 *
 * <p>转换发生在属主服务（creator-service）的出口处：它内部照常用 {@code PageDTO}，
 * 返回前转成本类。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "分页结果")
public class PageResult<T> {

    @ApiModelProperty("总记录数")
    private Long total;

    @ApiModelProperty("总页数")
    private Long pages;

    @ApiModelProperty("当前页数据")
    private List<T> list;

    /**
     * 空页（上游不可用时的降级返回值）
     */
    public static <T> PageResult<T> empty() {
        return new PageResult<>(0L, 0L, Collections.emptyList());
    }
}
