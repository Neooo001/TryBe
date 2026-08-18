package com.trybe.bodyprofile.controller;

import com.trybe.api.dto.BodyProfileDTO;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.domain.R;
import com.trybe.common.utils.UserContext;
import com.trybe.bodyprofile.domain.dto.UpsertProfileReq;
import com.trybe.bodyprofile.domain.vo.BodyProfileVO;
import com.trybe.bodyprofile.service.IBodyProfileService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * <p>
 * 身形档案控制器 — 单档案（每用户一条）
 * </p>
 */
@Api(tags = "身形档案相关接口")
@RestController
@RequestMapping("/profiles")
@RequiredArgsConstructor
@Slf4j
@Validated
public class BodyProfileController {

    private final IBodyProfileService profileService;

    @ApiOperation("获取我的档案（含关联素材）")
    @GetMapping("/mine")
    public R<BodyProfileVO> getMyProfile() {
        Long userId = requireLogin();
        BodyProfileVO vo = profileService.getMyProfile(userId);
        return R.ok(vo);
    }

    @ApiOperation("保存我的身型档案（不存在则创建）")
    @PutMapping("/mine")
    public R<BodyProfileVO> upsertBody(@RequestBody UpsertProfileReq req) {
        Long userId = requireLogin();
        log.info("[BodyProfile] 保存身型档案 userId={}", userId);
        BodyProfileVO vo = profileService.upsertBody(userId, req);
        return R.ok(vo);
    }

    @ApiOperation("关联素材到我的档案")
    @PostMapping("/mine/materials")
    public R<BodyProfileVO> linkMaterial(@RequestBody Map<String, Long> body) {
        Long userId = requireLogin();
        Long materialId = body.get("materialId");
        log.info("[BodyProfile] 关联素材 userId={} materialId={}", userId, materialId);
        BodyProfileVO vo = profileService.linkMaterial(userId, materialId);
        return R.ok(vo);
    }

    @ApiOperation("解除素材与档案的关联")
    @DeleteMapping("/mine/materials/{materialId}")
    public R<Void> unlinkMaterial(@ApiParam("素材ID") @PathVariable Long materialId) {
        Long userId = requireLogin();
        log.info("[BodyProfile] 解除素材关联 userId={} materialId={}", userId, materialId);
        profileService.unlinkMaterial(userId, materialId);
        return R.ok();
    }

    @ApiOperation("设置素材为主素材")
    @PutMapping("/mine/materials/{materialId}/primary")
    public R<BodyProfileVO> setPrimaryMaterial(@ApiParam("素材ID") @PathVariable Long materialId) {
        Long userId = requireLogin();
        log.info("[BodyProfile] 设置主素材 userId={} materialId={}", userId, materialId);
        BodyProfileVO vo = profileService.setPrimaryMaterial(userId, materialId);
        return R.ok(vo);
    }

    // ==================== Feign 服务间调用接口 ====================

    @ApiOperation("获取用户档案详情（Feign内部调用）")
    @GetMapping("/feign/mine")
    public BodyProfileDTO getMyProfileForFeign(@RequestParam("userId") Long userId) {
        log.info("[BodyProfile] Feign获取档案 userId={}", userId);
        BodyProfileVO vo = profileService.getMyProfile(userId);
        if (vo == null) {
            return null;
        }
        BodyProfileDTO dto = new BodyProfileDTO();
        dto.setProfileId(vo.getProfileId());
        dto.setUserId(vo.getUserId());
        dto.setName(vo.getName());
        dto.setHeight(vo.getHeight());
        dto.setWeight(vo.getWeight());
        dto.setBust(vo.getBust());
        dto.setWaist(vo.getWaist());
        dto.setHip(vo.getHip());
        dto.setShoulder(vo.getShoulder());
        dto.setArm(vo.getArm());
        dto.setLeg(vo.getLeg());
        dto.setBodyType(vo.getBodyType());
        dto.setSkinTone(vo.getSkinTone());
        dto.setModifyParts(vo.getModifyParts());
        dto.setRemark(vo.getRemark());
        dto.setMaterials(vo.getMaterials() == null ? null : vo.getMaterials().stream().map(m -> {
            com.trybe.api.dto.BodyProfileMaterialDTO mdto = new com.trybe.api.dto.BodyProfileMaterialDTO();
            mdto.setMaterialId(m.getMaterialId());
            mdto.setMaterialType(m.getMaterialType());
            mdto.setIsPrimary(m.getIsPrimary());
            mdto.setSortOrder(m.getSortOrder());
            mdto.setAccessUrl(m.getAccessUrl());
            return mdto;
        }).collect(java.util.stream.Collectors.toList()));
        return dto;
    }

    private Long requireLogin() {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new com.trybe.common.exception.UnauthorizedException(ErrorCodeEnum.NOT_LOGIN, "请先登录");
        }
        return userId;
    }
}
