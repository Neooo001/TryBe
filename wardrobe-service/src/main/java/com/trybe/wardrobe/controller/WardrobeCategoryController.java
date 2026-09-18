package com.trybe.wardrobe.controller;

import com.trybe.common.domain.R;
import com.trybe.common.utils.UserContext;
import com.trybe.wardrobe.domain.dto.CategoryNodeReq;
import com.trybe.wardrobe.domain.vo.CategoryVO;
import com.trybe.wardrobe.service.WardrobeCategoryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 衣服分类树控制器
 * </p>
 *
 * <p>对应小程序「分类」弹层与「编辑分类 / 编辑二级分类」页面。
 * 用户首次访问时服务端自动按模板初始化一套分类，前端拿到的是可直接渲染的树。
 *
 * <p>注意路径用字面量 {@code /wardrobe/categories}，
 * Spring MVC 会优先匹配字面量而非 {@code /wardrobe/{wardrobeId}}，两者不冲突。
 */
@Api(tags = "衣服分类接口")
@RestController
@RequestMapping("/wardrobe/categories")
@RequiredArgsConstructor
@Slf4j
@Validated
public class WardrobeCategoryController {

    private final WardrobeCategoryService categoryService;

    @ApiOperation("查询分类树（首次访问自动初始化）")
    @GetMapping
    public R<List<CategoryVO>> list() {
        Long userId = UserContext.requireUser();
        return R.ok(categoryService.listTree(userId));
    }

    @ApiOperation("新建分类（parentId 为空或 0 为一级分类）")
    @PostMapping
    public R<CategoryVO> create(@RequestBody CategoryNodeReq req) {
        Long userId = UserContext.requireUser();
        log.info("[WardrobeCategory] 新建请求 userId={} parentId={} name={}", userId, req.getParentId(), req.getName());
        return R.ok(categoryService.createNode(userId, req));
    }

    @ApiOperation("批量保存分类（重命名 + 拖拽排序）")
    @PutMapping
    public R<List<CategoryVO>> batchSave(@RequestBody List<CategoryNodeReq> nodes) {
        Long userId = UserContext.requireUser();
        log.info("[WardrobeCategory] 批量保存请求 userId={} count={}", userId, nodes == null ? 0 : nodes.size());
        return R.ok(categoryService.batchSave(userId, nodes));
    }

    @ApiOperation("删除分类（级联删除其二级分类）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@ApiParam("分类ID") @PathVariable Long id) {
        Long userId = UserContext.requireUser();
        log.info("[WardrobeCategory] 删除请求 userId={} id={}", userId, id);
        categoryService.deleteNode(userId, id);
        return R.ok();
    }
}
