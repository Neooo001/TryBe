package com.trybe.creator.controller;

import com.trybe.common.domain.R;
import com.trybe.common.utils.UserContext;
import com.trybe.creator.domain.dto.PresignReq;
import com.trybe.creator.domain.dto.UploadConfirmReq;
import com.trybe.creator.domain.vo.PresignVO;
import com.trybe.creator.domain.vo.UploadConfirmVO;
import com.trybe.creator.service.UploadService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 创作者图片上传控制器（P1 预签名 / P2 确认）
 *
 * @author TryBe Team
 */
@Api(tags = "创作者图片上传接口")
@RestController
@RequestMapping("/creator/uploads")
@RequiredArgsConstructor
@Slf4j
@Validated
public class UploadController {

    private final UploadService uploadService;

    @ApiOperation("P1 预签名直传（返回 tmp 位 objectKey 与 PUT URL）")
    @PostMapping("/presign")
    public R<PresignVO> presign(@RequestBody @Validated PresignReq req) {
        Long creatorId = UserContext.requireUser();
        return R.ok(uploadService.presign(creatorId, req));
    }

    @ApiOperation("P2 上传确认（校验存在性/大小/类型，CUTOUT 场景做透明底兜底校验，通过后归档）")
    @PostMapping("/confirm")
    public R<UploadConfirmVO> confirm(@RequestBody @Validated UploadConfirmReq req) {
        Long creatorId = UserContext.requireUser();
        log.info("[Upload] 上传确认 creatorId={} scene={}", creatorId, req.getScene());
        return R.ok(uploadService.confirm(creatorId, req));
    }
}
