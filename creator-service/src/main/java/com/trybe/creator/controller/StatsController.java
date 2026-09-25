package com.trybe.creator.controller;

import com.trybe.common.domain.R;
import com.trybe.common.utils.UserContext;
import com.trybe.creator.domain.vo.CreatorStatsVO;
import com.trybe.creator.service.IStatsService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据看板控制器
 *
 * <p><b>双账号域拆分后单一角色化</b>：不再区分 CREATOR / ADMIN，
 * 取当前登录用户（realm=web，由网关按路径校验保障）即可。
 * 运营全局看板（{@code /creator/admin/stats/overview}）已下线。
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

    @ApiOperation("我的数据看板")
    @GetMapping("/stats/overview")
    public R<CreatorStatsVO> myOverview() {
        Long creatorId = UserContext.requireUser();
        return R.ok(statsService.creatorOverview(creatorId));
    }
}
