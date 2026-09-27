package com.trybe.creator.controller;

import com.trybe.common.domain.R;
import com.trybe.common.utils.UserContext;
import com.trybe.creator.domain.dto.CreatorProfileUpdateReq;
import com.trybe.creator.domain.vo.CreatorProfileVO;
import com.trybe.creator.service.ICreatorProfileService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 创作者个人中心控制器（个人主页资料 + 账号资料只读展示）
 *
 * <p>身份一律从 UserContext 取（禁止前端传 creatorId，数据归属由
 * uk_creator 唯一键定位）；域隔离由网关 realm 校验保障（/creator/** → web 域），
 * 与 ItemController / UploadController 同一约定。
 *
 * <p>头像与封面图复用 UploadController 的 P1 预签名 / P2 确认直传流程
 * （scene=AVATAR / COVER），确认归档后把 objectKey 回传到本控制器的更新接口绑定。
 *
 * @author TryBe Team
 */
@Api(tags = "创作者个人中心接口")
@RestController
@RequestMapping("/creator/profile")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ProfileController {

    private final ICreatorProfileService profileService;

    @ApiOperation("PF1 获取当前创作者个人中心资料（公开主页资料 + 账号资料 + 创作方向可选项；首访懒创建默认档案）")
    @GetMapping
    public R<CreatorProfileVO> getMyProfile() {
        Long creatorId = UserContext.requireUser();
        return R.ok(profileService.getMyProfile(creatorId));
    }

    @ApiOperation("PF2 更新当前创作者个人中心资料（白名单：昵称/头像/封面/简介/创作方向；"
            + "身份、主页展示状态、审核统计、账号类字段不接受修改）")
    @PutMapping
    public R<CreatorProfileVO> updateMyProfile(@RequestBody @Validated CreatorProfileUpdateReq req) {
        Long creatorId = UserContext.requireUser();
        log.info("[Profile] 更新个人中心资料 creatorId={}", creatorId);
        return R.ok(profileService.updateMyProfile(creatorId, req));
    }
}
