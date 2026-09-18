package com.trybe.user.controller;

import com.trybe.common.domain.R;
import com.trybe.common.utils.UserContext;
import com.trybe.user.domain.dto.CreatorApplyReq;
import com.trybe.user.domain.dto.LicensePresignReq;
import com.trybe.user.domain.vo.ApplicationVO;
import com.trybe.user.domain.vo.PresignVO;
import com.trybe.user.service.ICreatorApplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 创作者入驻控制器（U2~U4）
 *
 * <p>路径前缀 /users/creator，全部要求已登录（gateway 鉴权 + UserContext），
 * 角色由 UserContext 取 userId 后在服务层判定，禁止前端传 userId。
 *
 * @author TryBe Team
 */
@Api(tags = "创作者入驻相关接口")
@RestController
@RequestMapping("/users/creator")
@RequiredArgsConstructor
@Slf4j
@Validated
public class CreatorApplyController {

    private final ICreatorApplyService creatorApplyService;

    @ApiOperation("U2 入驻资质图预签名（OSS直传）")
    @PostMapping("/uploads/presign")
    public R<PresignVO> presignLicense(@RequestBody @Validated LicensePresignReq req) {
        Long userId = UserContext.requireUser();
        return R.ok(creatorApplyService.presignLicense(userId, req));
    }

    @ApiOperation("U3 提交创作者入驻申请")
    @PostMapping("/apply")
    public R<ApplicationVO> apply(@RequestBody @Validated CreatorApplyReq req) {
        Long userId = UserContext.requireUser();
        log.info("[CreatorApply] 提交入驻申请 userId={}", userId);
        return R.ok(creatorApplyService.apply(userId, req));
    }

    @ApiOperation("U4 查询我的入驻申请（无记录返回 data=null）")
    @GetMapping("/application")
    public R<ApplicationVO> getMyApplication() {
        Long userId = UserContext.requireUser();
        return R.ok(creatorApplyService.getMyApplication(userId));
    }
}
