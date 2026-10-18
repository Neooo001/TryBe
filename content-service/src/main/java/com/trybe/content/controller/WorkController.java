package com.trybe.content.controller;

import com.trybe.api.dto.PageResult;
import com.trybe.api.dto.WorkBriefDTO;
import com.trybe.api.dto.WorkDetailDTO;
import com.trybe.api.dto.WorkQuery;
import com.trybe.common.domain.R;
import com.trybe.content.service.IWorkService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端作品浏览接口（小程序主页 / 圈子）
 *
 * <p>路径前缀 {@code /community/**} 在网关上<b>免鉴权放行</b>（浏览内容不需要登录），
 * 因此这里的每一个出参字段都必须是「本来就要给所有人看的公开内容」：
 * 作品标题 / 描述 / 封面 / 标签 + 创作者公开主页信息。
 * 价格、外链、AI 解析结果一律不带（见 {@link WorkBriefDTO}）。
 *
 * <p>本轮只做列表与筛选，<b>不做作品详情、不做点赞/关注/评论</b>。
 *
 * @author TryBe Team
 */
@Api(tags = "C 端作品浏览接口")
@RestController
@RequestMapping("/community/works")
@RequiredArgsConstructor
@Validated
public class WorkController {

    private final IWorkService workService;

    @ApiOperation("分页浏览公开作品（按发布时间倒序）")
    @GetMapping
    public R<PageResult<WorkBriefDTO>> pageWorks(@Validated WorkQuery query) {
        return R.ok(workService.pageWorks(query));
    }

    @ApiOperation("作品详情（小程序作品详情页）")
    @GetMapping("/{id}")
    public R<WorkDetailDTO> getWorkDetail(@ApiParam("作品ID") @PathVariable Long id) {
        return R.ok(workService.getWorkDetail(id));
    }

    @ApiOperation("作品分区字典（首页/圈子筛选 tab）")
    @GetMapping("/zones")
    public R<List<String>> listZones() {
        return R.ok(workService.listZones());
    }
}
