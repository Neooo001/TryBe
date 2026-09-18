package com.trybe.user.controller;

import com.trybe.common.domain.PageDTO;
import com.trybe.common.domain.PageQuery;
import com.trybe.common.domain.R;
import com.trybe.common.utils.UserContext;
import com.trybe.user.domain.dto.ReviewApplicationReq;
import com.trybe.user.domain.vo.ApplicationVO;
import com.trybe.user.service.ICreatorApplyService;
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

/**
 * 运营端创作者入驻审核控制器（U5~U6）
 *
 * <p>ADMIN 守卫在 CreatorApplyServiceImpl 内完成（查 user.role + Redis 缓存），
 * 非运营返回 FORBIDDEN(401)。
 *
 * @author TryBe Team
 */
@Api(tags = "运营端创作者入驻审核接口")
@RestController
@RequestMapping("/users/admin/creator")
@RequiredArgsConstructor
@Slf4j
@Validated
public class AdminCreatorController {

    private final ICreatorApplyService creatorApplyService;

    @ApiOperation("U5 入驻申请审核列表")
    @GetMapping("/applications")
    public R<PageDTO<ApplicationVO>> pageApplications(
            @ApiParam("申请状态：PENDING/APPROVED/REJECTED，可空") @RequestParam(required = false) String status,
            PageQuery query) {
        Long adminId = UserContext.requireUser();
        return R.ok(creatorApplyService.pageApplications(adminId, status, query));
    }

    @ApiOperation("U6 入驻申请审核（通过/驳回）")
    @PostMapping("/applications/{id}/review")
    public R<Void> review(@ApiParam("申请单ID") @PathVariable Long id,
                          @RequestBody @Validated ReviewApplicationReq req) {
        Long adminId = UserContext.requireUser();
        log.info("[AdminCreator] 入驻审核 applicationId={} approve={} adminId={}", id, req.getApprove(), adminId);
        creatorApplyService.reviewApplication(adminId, id, req);
        return R.ok();
    }
}
