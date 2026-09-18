package com.trybe.creator.controller;

import com.trybe.common.domain.R;
import com.trybe.creator.domain.vo.AdminStatsVO;
import com.trybe.creator.domain.vo.CreatorStatsVO;
import com.trybe.creator.service.IStatsService;
import com.trybe.creator.util.RoleGuard;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据看板控制器（S1 创作者 / S2 运营）
 *
 * @author TryBe Team
 */
@Api(tags = "创作者数据看板接口")
@RestController
@RequestMapping("/creator")
@RequiredArgsConstructor
@Slf4j
@Validated
public class StatsController {

    private final IStatsService statsService;
    private final RoleGuard roleGuard;

    @ApiOperation("S1 我的数据看板")
    @GetMapping("/stats/overview")
    public R<CreatorStatsVO> myOverview() {
        Long creatorId = roleGuard.requireCreator();
        return R.ok(statsService.creatorOverview(creatorId));
    }

    @ApiOperation("S2 运营全局看板")
    @GetMapping("/admin/stats/overview")
    public R<AdminStatsVO> adminOverview() {
        Long adminId = roleGuard.requireAdmin();
        log.info("[Stats] 运营看板查询 adminId={}", adminId);
        return R.ok(statsService.adminOverview());
    }
}
