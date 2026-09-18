package com.trybe.wardrobe.controller;

import com.trybe.common.domain.R;
import com.trybe.common.utils.UserContext;
import com.trybe.wardrobe.domain.dto.UpdateWardrobeReq;
import com.trybe.wardrobe.domain.vo.ImportTaskVO;
import com.trybe.wardrobe.domain.vo.WardrobeItemVO;
import com.trybe.wardrobe.service.WardrobeImportService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * <p>
 * 衣服导入控制器 —— 「导入衣服」功能的入口
 * </p>
 *
 * <p>四步式接口，全部由前端主动驱动，无需 WebSocket 推送：
 * <ol>
 *   <li>{@code POST /wardrobe/import/upload} — 上传图片，秒回 taskId</li>
 *   <li>{@code GET  /wardrobe/import/tasks/{taskId}} — 轮询进度，成功后直接拿到识别结果（未落库）</li>
 *   <li>{@code POST /wardrobe/import/tasks/{taskId}/commit} — 用户确认后<b>才</b>落库为正式条目</li>
 *   <li>{@code POST /wardrobe/import/tasks/{taskId}/retry} — 识别不满意时重跑</li>
 * </ol>
 */
@Api(tags = "衣服导入接口")
@RestController
@RequestMapping("/wardrobe/import")
@RequiredArgsConstructor
@Slf4j
@Validated
public class WardrobeImportController {

    private final WardrobeImportService importService;

    @ApiOperation("上传衣服图片并创建AI识别任务")
    @PostMapping("/upload")
    public R<ImportTaskVO> upload(@ApiParam(value = "衣服图片", required = true) @RequestParam("image") MultipartFile image) {
        Long userId = UserContext.requireUser();
        log.info("[WardrobeImport] 上传请求 userId={} filename={} size={}",
                userId, image.getOriginalFilename(), image.getSize());
        return R.ok(importService.createTask(userId, image));
    }

    @ApiOperation("查询导入任务进度与识别结果")
    @GetMapping("/tasks/{taskId}")
    public R<ImportTaskVO> getTask(@ApiParam("任务ID") @PathVariable Long taskId) {
        Long userId = UserContext.requireUser();
        return R.ok(importService.getTask(userId, taskId));
    }

    @ApiOperation("确认入库（用户点「放入衣橱」时调用，幂等）")
    @PostMapping("/tasks/{taskId}/commit")
    public R<WardrobeItemVO> commit(@ApiParam("任务ID") @PathVariable Long taskId,
                                    @ApiParam("用户确认后的表单（复用衣柜编辑请求体）")
                                    @RequestBody(required = false) UpdateWardrobeReq req) {
        Long userId = UserContext.requireUser();
        log.info("[WardrobeImport] 确认入库请求 userId={} taskId={}", userId, taskId);
        return R.ok(importService.commit(userId, taskId, req));
    }

    @ApiOperation("重新识别")
    @PostMapping("/tasks/{taskId}/retry")
    public R<ImportTaskVO> retry(@ApiParam("任务ID") @PathVariable Long taskId) {
        Long userId = UserContext.requireUser();
        log.info("[WardrobeImport] 重新识别请求 userId={} taskId={}", userId, taskId);
        return R.ok(importService.retry(userId, taskId));
    }

    @ApiOperation("放弃导入（清理草稿与图片）")
    @DeleteMapping("/tasks/{taskId}")
    public R<Void> discard(@ApiParam("任务ID") @PathVariable Long taskId) {
        Long userId = UserContext.requireUser();
        log.info("[WardrobeImport] 放弃导入请求 userId={} taskId={}", userId, taskId);
        importService.discard(userId, taskId);
        return R.ok();
    }
}
