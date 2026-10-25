package com.trybe.creator.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.trybe.api.dto.MyWorkBriefDTO;
import com.trybe.api.dto.PageResult;
import com.trybe.common.domain.PageDTO;
import com.trybe.common.domain.R;
import com.trybe.common.exception.BadRequestException;
import com.trybe.common.utils.UserContext;
import com.trybe.creator.domain.dto.ItemPageQuery;
import com.trybe.creator.domain.enums.CreatorItemStatus;
import com.trybe.creator.domain.vo.ItemListVO;
import com.trybe.creator.service.ICreatorItemService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 小程序「我的作品」接口（<b>服务间调用，不对公网开放</b>）
 *
 * <p>由 content-service 经 Feign 调用，供小程序「我的」页面查看并上下架自己的作品。
 *
 * <p><b>为什么不直接复用 {@link ItemController}</b>：
 * <ol>
 *   <li>{@code /creator/**} 在网关上属于 web 域，小程序令牌（realm=wx）进不来；
 *       这里走注册中心内部直连，不经过网关，天然无域冲突</li>
 *   <li>本次需求<b>只开放「看 + 上架 + 下架」</b>。{@link ItemController} 还挂着新建 / 编辑 /
 *       删除 / 发布，把整条 {@code /creator/**} 放行给小程序等于把编辑权限一起给了出去</li>
 *   <li>出参换成契约层的 {@link MyWorkBriefDTO}，不把 creator-service 的 domain VO 泄漏出去</li>
 * </ol>
 *
 * <p><b>草稿不出现在这里</b>：{@code status} 只接受 ON_SHELF / OFF_SHELF，空值默认 ON_SHELF。
 * 草稿要走 I6「发布」（含必填校验 + 触发 AI 商品信息解析），语义与 I7「上架」不同，
 * 不在小程序端开放。
 *
 * <p>⚠️ 网关<b>没有</b> {@code /internal/**} 的路由，外部请求直接 404。
 * 也不得把这里的路径改到 {@code /creator/**} 下，那等于把作品管理口挂到公网。
 *
 * @author TryBe Team
 */
@Api(tags = "我的作品接口（内部，小程序专用）")
@RestController
@RequestMapping("/internal/creator/my-works")
@RequiredArgsConstructor
@Slf4j
public class InternalMyWorkController {

    private final ICreatorItemService creatorItemService;

    @ApiOperation("分页查询我的作品（仅已上架 / 已下架，按创建时间倒序）")
    @GetMapping
    public R<PageResult<MyWorkBriefDTO>> pageMyWorks(
            @ApiParam("状态：ON_SHELF / OFF_SHELF，空值默认 ON_SHELF") @RequestParam(value = "status", required = false) String status,
            @ApiParam("页码，从 1 开始") @RequestParam(value = "pageNo", required = false, defaultValue = "1") Integer pageNo,
            @ApiParam("每页数量") @RequestParam(value = "pageSize", required = false, defaultValue = "20") Integer pageSize) {

        Long creatorId = UserContext.requireUser();
        String safeStatus = requireVisibleStatus(status);

        ItemPageQuery query = new ItemPageQuery();
        query.setStatus(safeStatus);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);

        PageDTO<ItemListVO> page = creatorItemService.pageMyItems(creatorId, query);
        return R.ok(toPageResult(page));
    }

    @ApiOperation("上架我的作品（仅已下架可上架）")
    @PostMapping("/{id}/on-shelf")
    public R<Void> onShelf(@ApiParam("作品ID") @PathVariable("id") Long id) {
        Long creatorId = UserContext.requireUser();
        creatorItemService.onShelf(creatorId, id);
        log.info("[my-work] 小程序上架作品 id={} creatorId={}", id, creatorId);
        return R.ok();
    }

    @ApiOperation("下架我的作品（仅已上架可下架）")
    @PostMapping("/{id}/off-shelf")
    public R<Void> offShelf(@ApiParam("作品ID") @PathVariable("id") Long id) {
        Long creatorId = UserContext.requireUser();
        creatorItemService.offShelf(creatorId, id);
        log.info("[my-work] 小程序下架作品 id={} creatorId={}", id, creatorId);
        return R.ok();
    }

    // ==================== 私有方法 ====================

    /**
     * 状态白名单校验：只放 ON_SHELF / OFF_SHELF，空值兜底 ON_SHELF
     *
     * <p>这里是挡 DRAFT 的唯一闸口。不做这层校验的话，小程序只要传
     * {@code status=DRAFT} 就能把草稿翻出来——而草稿在小程序里既不能编辑也不能上架，
     * 列出来只会制造「为什么点不动」的困惑。
     */
    private String requireVisibleStatus(String status) {
        if (StrUtil.isBlank(status)) {
            return CreatorItemStatus.ON_SHELF.getCode();
        }
        String trimmed = status.trim().toUpperCase();
        if (CreatorItemStatus.ON_SHELF.getCode().equals(trimmed)
                || CreatorItemStatus.OFF_SHELF.getCode().equals(trimmed)) {
            return trimmed;
        }
        log.warn("[my-work] 非法状态参数 status={}", status);
        throw new BadRequestException("仅支持查看已上架 / 已下架的作品");
    }

    /**
     * {@code PageDTO} → 契约层 {@link PageResult}，并逐条转成 C 端卡片
     *
     * <p>必须转结构：{@code PageDTO} 的静态方法签名牵着 MyBatis-Plus 的 {@code Page} 类，
     * 而调用方 content-service 刻意不引持久层依赖，让它反序列化会直接
     * {@code NoClassDefFoundError}。
     */
    private PageResult<MyWorkBriefDTO> toPageResult(PageDTO<ItemListVO> page) {
        if (page == null) {
            return PageResult.empty();
        }
        List<MyWorkBriefDTO> list = page.getList() == null
                ? Collections.emptyList()
                : page.getList().stream().map(this::toMyWorkBrief).collect(Collectors.toList());
        return new PageResult<>(page.getTotal(), page.getPages(), list);
    }

    /**
     * 创作者视图 → C 端卡片
     *
     * <p>按钮显隐由状态机算，前端不重写一遍规则：
     * 已下架才能上架，已上架才能下架，草稿两个都点不了（本来也查不到）。
     */
    private MyWorkBriefDTO toMyWorkBrief(ItemListVO item) {
        CreatorItemStatus status = CreatorItemStatus.of(item.getStatus());

        MyWorkBriefDTO dto = new MyWorkBriefDTO();
        dto.setId(item.getId());
        dto.setItemNo(item.getItemNo());
        dto.setTitle(item.getTitle());
        dto.setDescription(item.getDescription());
        dto.setStatus(status == null ? item.getStatus() : status.getCode());
        dto.setStatusDesc(status == null ? "" : status.getDesc());
        dto.setZone(item.getZone());
        dto.setTags(parseTags(item.getPatternTags()));
        dto.setImageUrl(item.getImageUrl());
        dto.setCreateTime(item.getCreateTime());
        dto.setCanOnShelf(status != null && status.canOnShelf());
        dto.setCanOffShelf(status != null && status.canOffShelf());
        return dto;
    }

    /**
     * patternTags 在库里是 JSON 数组字符串（如 {@code ["复古","通勤"]}）
     *
     * <p>解析失败按「没有标签」处理：标签只是展示用的附加信息，
     * 不值得因为它写脏了就让整个列表报错。
     */
    private List<String> parseTags(String raw) {
        if (StrUtil.isBlank(raw)) {
            return Collections.emptyList();
        }
        try {
            return JSONUtil.toList(raw, String.class);
        } catch (Exception e) {
            log.warn("[my-work] 标签 JSON 解析失败 raw={}", raw);
            return Collections.emptyList();
        }
    }
}
