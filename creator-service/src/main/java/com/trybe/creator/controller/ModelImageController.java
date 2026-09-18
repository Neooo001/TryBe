package com.trybe.creator.controller;

import com.trybe.common.domain.R;
import com.trybe.creator.domain.dto.ModelImageBindReq;
import com.trybe.creator.domain.vo.ModelImageVO;
import com.trybe.creator.service.IModelImageService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模特图控制器（M1 绑定+同步质检 / M2 列表 / M3 删除）
 *
 * @author TryBe Team
 */
@Api(tags = "创作者模特图接口")
@RestController
@RequestMapping("/creator")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ModelImageController {

    private final IModelImageService modelImageService;
    private final RoleGuard roleGuard;

    @ApiOperation("M1 绑定模特图（需确认肖像授权，落库后同步质检）")
    @PostMapping("/items/{id}/model-images")
    public R<ModelImageVO> bind(@ApiParam("物品ID") @PathVariable Long id,
                                @RequestBody @Validated ModelImageBindReq req) {
        Long creatorId = roleGuard.requireCreator();
        log.info("[ModelImage] 绑定模特图 itemId={} creatorId={}", id, creatorId);
        return R.ok(modelImageService.bind(creatorId, id, req));
    }

    @ApiOperation("M2 模特图列表")
    @GetMapping("/items/{id}/model-images")
    public R<List<ModelImageVO>> listByItem(@ApiParam("物品ID") @PathVariable Long id) {
        Long creatorId = roleGuard.requireCreator();
        // 归属校验下沉到服务层：非本人物品返回 402，避免越权读取模特图
        return R.ok(modelImageService.listMyItemImages(creatorId, id));
    }

    @ApiOperation("M3 删除模特图（仅本人）")
    @DeleteMapping("/model-images/{imageId}")
    public R<Void> deleteImage(@ApiParam("模特图ID") @PathVariable Long imageId) {
        Long creatorId = roleGuard.requireCreator();
        log.info("[ModelImage] 删除模特图 imageId={} creatorId={}", imageId, creatorId);
        modelImageService.deleteImage(creatorId, imageId);
        return R.ok();
    }
}
