package com.trybe.creator.controller;

import com.trybe.common.domain.PageDTO;
import com.trybe.common.domain.R;
import com.trybe.creator.domain.dto.ItemPageQuery;
import com.trybe.creator.domain.dto.ItemSaveReq;
import com.trybe.creator.domain.vo.ItemDetailVO;
import com.trybe.creator.domain.vo.ItemListVO;
import com.trybe.creator.service.ICreatorItemService;
import com.trybe.creator.util.RoleGuard;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 创作者物品控制器（I1~I8）
 *
 * <p>身份一律从 UserContext 取（c罗③：禁止前端传 userId），角色由 RoleGuard 守卫。
 *
 * @author TryBe Team
 */
@Api(tags = "创作者物品管理接口")
@RestController
@RequestMapping("/creator/items")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ItemController {

    private final ICreatorItemService creatorItemService;
    private final RoleGuard roleGuard;

    @ApiOperation("I1 我的物品列表（分页/筛选）")
    @GetMapping
    public R<PageDTO<ItemListVO>> pageMyItems(ItemPageQuery query) {
        Long creatorId = roleGuard.requireCreator();
        return R.ok(creatorItemService.pageMyItems(creatorId, query));
    }

    @ApiOperation("I2 物品详情")
    @GetMapping("/{id}")
    public R<ItemDetailVO> getMyItem(@ApiParam("物品ID") @PathVariable Long id) {
        Long creatorId = roleGuard.requireCreator();
        return R.ok(creatorItemService.getMyItem(creatorId, id));
    }

    @ApiOperation("I3 新建物品（草稿）")
    @PostMapping
    public R<ItemDetailVO> createItem(@RequestBody @Validated ItemSaveReq req) {
        Long creatorId = roleGuard.requireCreator();
        log.info("[Item] 新建物品 creatorId={}", creatorId);
        return R.ok(creatorItemService.createItem(creatorId, req));
    }

    @ApiOperation("I4 编辑物品（字段为 null 表示不修改）")
    @PutMapping("/{id}")
    public R<ItemDetailVO> updateItem(@ApiParam("物品ID") @PathVariable Long id,
                                      @RequestBody @Validated ItemSaveReq req) {
        Long creatorId = roleGuard.requireCreator();
        log.info("[Item] 编辑物品 id={} creatorId={}", id, creatorId);
        return R.ok(creatorItemService.updateItem(creatorId, id, req));
    }

    @ApiOperation("I5 删除物品（逻辑删除）")
    @DeleteMapping("/{id}")
    public R<Void> deleteItem(@ApiParam("物品ID") @PathVariable Long id) {
        Long creatorId = roleGuard.requireCreator();
        log.info("[Item] 删除物品 id={} creatorId={}", id, creatorId);
        creatorItemService.deleteItem(creatorId, id);
        return R.ok();
    }

    @ApiOperation("I6 提交审核")
    @PostMapping("/{id}/submit")
    public R<Void> submit(@ApiParam("物品ID") @PathVariable Long id) {
        Long creatorId = roleGuard.requireCreator();
        creatorItemService.submit(creatorId, id);
        return R.ok();
    }

    @ApiOperation("I7 上架")
    @PostMapping("/{id}/on-shelf")
    public R<Void> onShelf(@ApiParam("物品ID") @PathVariable Long id) {
        Long creatorId = roleGuard.requireCreator();
        creatorItemService.onShelf(creatorId, id);
        return R.ok();
    }

    @ApiOperation("I8 下架")
    @PostMapping("/{id}/off-shelf")
    public R<Void> offShelf(@ApiParam("物品ID") @PathVariable Long id) {
        Long creatorId = roleGuard.requireCreator();
        creatorItemService.offShelf(creatorId, id);
        return R.ok();
    }
}
