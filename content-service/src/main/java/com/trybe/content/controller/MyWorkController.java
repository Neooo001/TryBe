package com.trybe.content.controller;

import com.trybe.api.dto.MyWorkBriefDTO;
import com.trybe.api.dto.PageResult;
import com.trybe.common.domain.R;
import com.trybe.common.utils.UserContext;
import com.trybe.content.service.IMyWorkService;
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

/**
 * 小程序「我的作品」接口
 *
 * <p>路径落在 {@code /community/**} 下，因此在网关属于 <b>wx 域</b>，
 * 小程序令牌天然可用，<b>不需要改动网关任何配置</b>。
 *
 * <p>与 {@link WorkController}（公开浏览）的区别：这里查的是「我自己」的作品，
 * 含已下架状态，且允许两个写操作（上架 / 下架）。因为路径同样在 {@code /community/**}
 * 下，安全边界必须靠代码自己守住，不能指望路径本身——所以：
 * <ul>
 *   <li>每个方法入口都调 {@code requireUser()} 硬断言登录。网关当前确实要求
 *       {@code /community/**} 登录才放行，但只要哪天有人把它加回白名单，
 *       这里就会变成「谁的令牌都不用就能看别人作品」的越权漏洞。</li>
 *   <li>用户身份<b>不接受前端传参</b>，只从网关透传的 {@code user-info} 头还原；
 *       再经 Feign 拦截器传给 creator-service，creator-service 侧还会校验作品归属</li>
 * </ul>
 *
 * <p>不提供「新建 / 编辑 / 删除 / 发布」：小程序的定位是日常开关，
 * 内容生产留在网页创作者后台。
 *
 * @author TryBe Team
 */
@Api(tags = "小程序我的作品接口")
@RestController
@RequestMapping("/community/my/works")
@RequiredArgsConstructor
@Slf4j
public class MyWorkController {

    private final IMyWorkService myWorkService;

    @ApiOperation("分页查询我的作品（默认已上架）")
    @GetMapping
    public R<PageResult<MyWorkBriefDTO>> pageMyWorks(
            @ApiParam("状态：ON_SHELF 已上架 / OFF_SHELF 已下架，空值默认已上架")
            @RequestParam(value = "status", required = false) String status,
            @ApiParam("页码，从 1 开始")
            @RequestParam(value = "pageNo", required = false, defaultValue = "1") Integer pageNo,
            @ApiParam("每页数量")
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") Integer pageSize) {
        UserContext.requireUser();
        return R.ok(myWorkService.pageMyWorks(status, pageNo, pageSize));
    }

    @ApiOperation("上架作品（仅已下架可上架）")
    @PostMapping("/{id}/on-shelf")
    public R<Void> onShelf(@ApiParam("作品ID") @PathVariable("id") Long id) {
        Long userId = UserContext.requireUser();
        log.info("[my-work] 上架请求 id={} userId={}", id, userId);
        myWorkService.onShelf(id);
        return R.ok();
    }

    @ApiOperation("下架作品（仅已上架可下架）")
    @PostMapping("/{id}/off-shelf")
    public R<Void> offShelf(@ApiParam("作品ID") @PathVariable("id") Long id) {
        Long userId = UserContext.requireUser();
        log.info("[my-work] 下架请求 id={} userId={}", id, userId);
        myWorkService.offShelf(id);
        return R.ok();
    }
}
