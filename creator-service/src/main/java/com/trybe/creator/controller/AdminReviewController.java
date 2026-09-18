package com.trybe.creator.controller;

import com.trybe.common.domain.PageDTO;
import com.trybe.common.domain.R;
import com.trybe.creator.domain.dto.ItemPageQuery;
import com.trybe.creator.domain.dto.ReviewReq;
import com.trybe.creator.domain.vo.AuditRecordVO;
import com.trybe.creator.domain.vo.ItemDetailVO;
import com.trybe.creator.domain.vo.ItemListVO;
import com.trybe.creator.service.IAuditRecordService;
import com.trybe.creator.service.ICreatorItemService;
import com.trybe.creator.util.RoleGuard;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运营审核中心控制器（R1~R4）
 *
 * @author TryBe Team
 */
@Api(tags = "创作者审核中心接口")
@RestController
@RequestMapping("/creator/admin")
@RequiredArgsConstructor
@Slf4j
@Validated
public class AdminReviewController {

    /** v1 唯一审核对象类型 */
    private static final String OBJECT_TYPE_CREATOR_ITEM = "CREATOR_ITEM";

    private final ICreatorItemService creatorItemService;
    private final IAuditRecordService auditRecordService;
    private final RoleGuard roleGuard;

    @ApiOperation("R1 待审队列（含创作者昵称）")
    @GetMapping("/items/pending")
    public R<PageDTO<ItemListVO>> pagePendingItems(ItemPageQuery query) {
        Long adminId = roleGuard.requireAdmin();
        log.info("[AdminReview] 查询待审队列 adminId={}", adminId);
        return R.ok(creatorItemService.pagePendingItems(query));
    }

    @ApiOperation("R2 物品审核详情（含模特图质检明细与授权记录）")
    @GetMapping("/items/{id}")
    public R<ItemDetailVO> getItemDetail(@ApiParam("物品ID") @PathVariable Long id) {
        Long adminId = roleGuard.requireAdmin();
        return R.ok(creatorItemService.getAdminItem(id));
    }

    @ApiOperation("R3 物品审核（通过/驳回）")
    @PostMapping("/items/{id}/review")
    public R<Void> review(@ApiParam("物品ID") @PathVariable Long id,
                          @RequestBody @Validated ReviewReq req) {
        Long adminId = roleGuard.requireAdmin();
        creatorItemService.review(adminId, id, req);
        return R.ok();
    }

    @ApiOperation("R4 审核流水")
    @GetMapping("/audits")
    public R<List<AuditRecordVO>> listAudits(
            @ApiParam("对象类型，默认 CREATOR_ITEM") @RequestParam(required = false) String objectType,
            @ApiParam("对象ID") @RequestParam Long objectId) {
        Long adminId = roleGuard.requireAdmin();
        String type = (objectType == null || objectType.trim().isEmpty())
                ? OBJECT_TYPE_CREATOR_ITEM : objectType.trim();
        return R.ok(auditRecordService.listByObject(type, objectId));
    }
}
