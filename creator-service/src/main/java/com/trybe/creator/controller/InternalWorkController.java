package com.trybe.creator.controller;

import com.trybe.api.dto.PageResult;
import com.trybe.api.dto.WorkBriefDTO;
import com.trybe.api.dto.WorkDetailDTO;
import com.trybe.api.dto.WorkQuery;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.domain.PageDTO;
import com.trybe.common.domain.R;
import com.trybe.creator.service.IPublicWorkReadService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * 作品公开读接口（<b>服务间调用，不对公网开放</b>）
 *
 * <p>由 content-service 经 Feign 调用，供小程序主页 / 圈子浏览创作者作品。
 *
 * <p><b>为什么单独开一个 internal 控制器，而不是复用 {@link ItemController}：</b>
 * <ol>
 *   <li>{@code /creator/**} 在网关上属于 web 域，小程序 token 进不来；这里走的是
 *       注册中心内部直连，不经过网关，天然无域冲突</li>
 *   <li>出参是「浏览者视图」{@link WorkBriefDTO}，与创作者私有的 {@code ItemListVO}
 *       字段完全不同，混在一起会互相污染（比如哪天误把价格/AI 字段带回 C 端）</li>
 * </ol>
 *
 * <p>⚠️ 网关<b>没有</b> {@code /internal/**} 的路由，外部请求直接 404，
 * 不需要也不应该再加 token 校验 —— 但也不得把这里的路径改到 {@code /creator/**} 下，
 * 那等于把只读数据出口挂到公网。
 *
 * @author TryBe Team
 */
@Api(tags = "作品公开读接口（内部）")
@RestController
@RequestMapping("/internal/creator/works")
@RequiredArgsConstructor
@Slf4j
@Validated
public class InternalWorkController {

    private final IPublicWorkReadService publicWorkReadService;

    @ApiOperation("分页查询已上架的公开作品（按发布时间倒序）")
    @GetMapping
    public R<PageResult<WorkBriefDTO>> pagePublicWorks(@Validated WorkQuery query) {
        return R.ok(toPageResult(publicWorkReadService.pagePublicWorks(query)));
    }

    /**
     * 出口处把 {@code PageDTO} 转成契约层的 {@link PageResult}
     *
     * <p>为什么必须转：{@code PageDTO} 的静态方法签名牵着 MyBatis-Plus 的 {@code Page} 类，
     * 而调用方 content-service 刻意不引持久层依赖 —— 让它反序列化 {@code PageDTO}
     * 会直接 {@code NoClassDefFoundError}。这里在属主服务出口转成无依赖的等价结构，
     * 既保住调用方的轻量，也让契约层不依赖任何 ORM。
     */
    private PageResult<WorkBriefDTO> toPageResult(PageDTO<WorkBriefDTO> page) {
        if (page == null) {
            return PageResult.empty();
        }
        return new PageResult<>(page.getTotal(), page.getPages(),
                page.getList() == null ? Collections.emptyList() : page.getList());
    }

    @ApiOperation("查询单个已上架作品的公开详情（小程序作品详情页）")
    @GetMapping("/{id}")
    public R<WorkDetailDTO> getPublicWorkDetail(@ApiParam("作品ID") @PathVariable Long id) {
        WorkDetailDTO detail = publicWorkReadService.getPublicWorkDetail(id);
        if (detail == null) {
            // 不存在 / 草稿 / 已下架 / 已删除，对 C 端一律回答「没有」，不区分原因
            return R.error(ErrorCodeEnum.DATA_NOT_FOUND);
        }
        return R.ok(detail);
    }

    @ApiOperation("作品分区字典")
    @GetMapping("/zones")
    public R<List<String>> listZones() {
        return R.ok(publicWorkReadService.listZones());
    }
}
