package com.trybe.material.controller;

import com.trybe.api.dto.BatchMaterialResult;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.domain.PageDTO;
import com.trybe.common.domain.PageQuery;
import com.trybe.common.domain.R;
import com.trybe.common.utils.UserContext;
import com.trybe.material.domain.dto.PreSignUploadReq;
import com.trybe.material.domain.dto.UploadCallbackReq;
import com.trybe.material.domain.vo.MaterialVO;
import com.trybe.material.domain.vo.PreSignUploadVO;
import com.trybe.material.service.IMaterialService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * <p>
 * 素材中心控制器
 * </p>
 */
@Api(tags = "素材中心相关接口")
@RestController
@RequestMapping("/materials")
@RequiredArgsConstructor
@Slf4j
@Validated
public class MaterialController {

    private final IMaterialService materialService;

    @ApiOperation("生成预签名上传URL")
    @PostMapping("/upload/pre-sign")
    public R<PreSignUploadVO> preSignUpload(@RequestBody @Valid PreSignUploadReq req) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            return R.error(ErrorCodeEnum.NOT_LOGIN, "请先登录");
        }
        log.info("[Material] 预签名上传请求 userId={} materialType={} fileName={}", userId, req.getMaterialType(), req.getFileName());
        PreSignUploadVO vo = materialService.generatePreSignUploadUrl(userId, req);
        return R.ok(vo);
    }

    @ApiOperation("上传成功回调（客户端直传OSS完成后调用）")
    @PostMapping("/upload/callback")
    public R<Void> uploadCallback(@RequestBody @Valid UploadCallbackReq req) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            return R.error(ErrorCodeEnum.NOT_LOGIN, "请先登录");
        }
        log.info("[Material] 上传回调 userId={} materialId={}", userId, req.getMaterialId());
        materialService.uploadCallback(userId, req);
        return R.ok();
    }

    @ApiOperation("查询素材详情")
    @GetMapping("/{materialId}")
    public R<MaterialVO> getDetail(@ApiParam("素材ID") @PathVariable Long materialId) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            return R.error(ErrorCodeEnum.NOT_LOGIN, "请先登录");
        }
        MaterialVO vo = materialService.getMaterialDetail(userId, materialId);
        return R.ok(vo);
    }

    @ApiOperation("分页查询用户素材列表")
    @GetMapping
    public R<PageDTO<MaterialVO>> listMaterials(
            @ApiParam("素材类型：FACE-人脸, HAND-手部") @RequestParam(required = false) String materialType,
            @Valid PageQuery pageQuery) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            return R.error(ErrorCodeEnum.NOT_LOGIN, "请先登录");
        }
        PageDTO<MaterialVO> page = materialService.listUserMaterials(userId, materialType, pageQuery);
        return R.ok(page);
    }

    @ApiOperation("删除素材")
    @DeleteMapping("/{materialId}")
    public R<Void> deleteMaterial(@ApiParam("素材ID") @PathVariable Long materialId) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            return R.error(ErrorCodeEnum.NOT_LOGIN, "请先登录");
        }
        log.info("[Material] 删除素材 userId={} materialId={}", userId, materialId);
        materialService.deleteMaterial(userId, materialId);
        return R.ok();
    }

    // ==================== Feign 服务间调用接口 ====================

    @ApiOperation("批量校验素材有效性（Feign内部调用）")
    @GetMapping("/feign/validate")
    public BatchMaterialResult validateMaterials(
            @RequestParam("userId") Long userId,
            @RequestParam("materialIds") List<Long> materialIds) {
        log.info("[Material] Feign批量校验素材 userId={} count={}", userId, materialIds != null ? materialIds.size() : 0);
        return materialService.validateMaterials(userId, materialIds);
    }

    @ApiOperation("批量获取素材临时访问链接（Feign内部调用）")
    @GetMapping("/feign/access-urls")
    public BatchMaterialResult getMaterialAccessUrls(
            @RequestParam("userId") Long userId,
            @RequestParam("materialIds") List<Long> materialIds) {
        log.info("[Material] Feign批量获取素材访问链接 userId={} count={}", userId, materialIds != null ? materialIds.size() : 0);
        return materialService.getMaterialAccessUrls(userId, materialIds);
    }

    @ApiOperation("删除素材（Feign内部调用，供档案服务级联清理孤儿素材）")
    @DeleteMapping("/feign/{materialId}")
    public void deleteMaterialForFeign(@PathVariable("materialId") Long materialId,
                                       @RequestParam("userId") Long userId) {
        log.info("[Material] Feign删除素材 userId={} materialId={}", userId, materialId);
        materialService.deleteMaterial(userId, materialId);
    }
}