package com.trybe.wardrobe.controller;

import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.domain.PageDTO;
import com.trybe.common.domain.PageQuery;
import com.trybe.common.domain.R;
import com.trybe.common.exception.UnauthorizedException;
import com.trybe.common.utils.UserContext;
import com.trybe.wardrobe.domain.dto.TaobaoImportReq;
import com.trybe.wardrobe.domain.dto.UpdateWardrobeReq;
import com.trybe.wardrobe.domain.vo.WardrobeItemVO;
import com.trybe.wardrobe.service.IWardrobeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;

/**
 * <p>
 * 衣柜控制器
 * </p>
 */
@Api(tags = "衣柜相关接口")
@RestController
@RequestMapping("/wardrobe")
@RequiredArgsConstructor
@Slf4j
@Validated
public class WardrobeController {

    private final IWardrobeService wardrobeService;

    @ApiOperation("淘宝分享链接导入")
    @PostMapping("/import/taobao")
    public R<WardrobeItemVO> importFromTaobao(@RequestBody @Valid TaobaoImportReq req) {
        Long userId = requireLogin();
        log.info("[Wardrobe] 淘宝导入请求 userId={}", userId);
        return R.ok(wardrobeService.importFromTaobao(userId, req));
    }

    @ApiOperation("手动添加")
    @PostMapping("/manual")
    public R<WardrobeItemVO> addManual(
            @RequestParam("title") String title,
            @RequestParam(value = "remark", required = false) String remark,
            @RequestParam("image") MultipartFile image) {
        Long userId = requireLogin();
        log.info("[Wardrobe] 手动添加请求 userId={}", userId);
        return R.ok(wardrobeService.addManual(userId, title, remark, image));
    }

    @ApiOperation("自定义穿搭上传")
    @PostMapping("/outfit")
    public R<WardrobeItemVO> addOutfit(
            @RequestParam("title") String title,
            @RequestParam(value = "remark", required = false) String remark,
            @RequestParam("image") MultipartFile image) {
        Long userId = requireLogin();
        log.info("[Wardrobe] 自定义穿搭请求 userId={}", userId);
        return R.ok(wardrobeService.addOutfit(userId, title, remark, image));
    }

    @ApiOperation("分页查询衣柜")
    @GetMapping
    public R<PageDTO<WardrobeItemVO>> listWardrobe(
            @ApiParam("来源：TAOBAO_IMPORT/MANUAL/CUSTOM_OUTFIT") @RequestParam(required = false) String source,
            @Valid PageQuery pageQuery) {
        Long userId = requireLogin();
        return R.ok(wardrobeService.listWardrobe(userId, source, pageQuery));
    }

    @ApiOperation("衣柜条目详情")
    @GetMapping("/{wardrobeId}")
    public R<WardrobeItemVO> getDetail(@ApiParam("衣柜条目ID") @PathVariable Long wardrobeId) {
        Long userId = requireLogin();
        return R.ok(wardrobeService.getDetail(userId, wardrobeId));
    }

    @ApiOperation("编辑衣柜条目")
    @PutMapping("/{wardrobeId}")
    public R<WardrobeItemVO> updateItem(@ApiParam("衣柜条目ID") @PathVariable Long wardrobeId,
                                        @RequestBody UpdateWardrobeReq req) {
        Long userId = requireLogin();
        log.info("[Wardrobe] 编辑请求 userId={} wardrobeId={}", userId, wardrobeId);
        return R.ok(wardrobeService.updateItem(userId, wardrobeId, req.getTitle(), req.getRemark()));
    }

    @ApiOperation("删除衣柜条目")
    @DeleteMapping("/{wardrobeId}")
    public R<Void> deleteItem(@ApiParam("衣柜条目ID") @PathVariable Long wardrobeId) {
        Long userId = requireLogin();
        log.info("[Wardrobe] 删除请求 userId={} wardrobeId={}", userId, wardrobeId);
        wardrobeService.deleteItem(userId, wardrobeId);
        return R.ok();
    }

    private Long requireLogin() {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new UnauthorizedException(ErrorCodeEnum.NOT_LOGIN, "请先登录");
        }
        return userId;
    }
}
