package com.trybe.user.controller;


import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.domain.R;
import com.trybe.common.service.OssService;
import com.trybe.common.utils.UserContext;
import com.trybe.user.domain.dto.UserProfileDTO;
import com.trybe.user.domain.dto.WechatLoginDTO;
import com.trybe.user.domain.po.User;
import com.trybe.user.domain.vo.UserLoginVO;
import com.trybe.user.domain.vo.UserProfileVO;
import com.trybe.user.service.AvatarUrlService;
import com.trybe.user.service.IUserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Api(tags = "用户相关接口")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final IUserService userService;
    private final OssService ossService;
    private final AvatarUrlService avatarUrlService;

    @ApiOperation("用户登录接口")
    @PostMapping("login")
    public R<UserLoginVO> login(@RequestBody @Validated WechatLoginDTO wechatLoginDTO) {
        log.info("登录表单: {}", wechatLoginDTO);
        String wxCode = wechatLoginDTO.getCode();
        if (wxCode == null || wxCode.isEmpty()) {
            return R.error(ErrorCodeEnum.PARAM_EMPTY, "登录凭证不能为空");
        }
        return R.ok(userService.loginByWechat(wxCode));
    }

    @ApiOperation("获取当前用户资料")
    @GetMapping("profile")
    public R<UserProfileVO> getProfile() {
        Long userId = UserContext.getUser();
        if (userId == null) {
            return R.error(ErrorCodeEnum.NOT_LOGIN, "请先登录");
        }
        log.info("获取用户资料: {}", userId);
        UserProfileVO vo = userService.getUserProfile(userId);
        if (vo == null) {
            return R.error(ErrorCodeEnum.DATA_NOT_FOUND, "用户不存在");
        }
        return R.ok(vo);
    }

    @ApiOperation("更新用户资料（支持头像上传）")
    @PostMapping("profile")
    public R<UserProfileVO> updateProfileWithAvatar(
            @RequestParam(value = "avatar", required = false) MultipartFile avatar,
            @RequestParam(value = "nickname", required = false) String nickname,
            @RequestParam(value = "bio", required = false) String bio,
            @RequestParam(value = "gender", required = false) Integer gender) {

        Long userId = UserContext.getUser();
        if (userId == null) {
            return R.error(ErrorCodeEnum.NOT_LOGIN, "请先登录");
        }
        log.info("更新用户资料(multipart): {}", userId);
        String avatarObjectKey = null;
        if (avatar != null && !avatar.isEmpty()) {
            avatarObjectKey = ossService.uploadAvatar(avatar, userId);
        }
        UserProfileVO vo = userService.updateProfile(userId, avatarObjectKey, nickname, bio, gender);
        return R.ok(vo);
    }

    @ApiOperation("更新用户资料（不含头像，JSON body）")
    @PutMapping("profile")
    public R<UserProfileVO> updateProfile(@RequestBody @Validated UserProfileDTO dto) {

        Long userId = UserContext.getUser();
        if (userId == null) {
            return R.error(ErrorCodeEnum.NOT_LOGIN, "请先登录");
        }
        log.info("更新用户资料(JSON): {}", userId);
        UserProfileVO vo = userService.updateProfile(userId, null, dto.getNickname(), dto.getBio(), dto.getGender());
        return R.ok(vo);
    }

    @ApiOperation("获取当前用户头像临时URL（URL过期后前端调用此接口刷新）")
    @GetMapping("avatar-url")
    public R<String> getAvatarUrl() {
        Long userId = UserContext.getUser();
        if (userId == null) {
            return R.error(ErrorCodeEnum.NOT_LOGIN, "请先登录");
        }
        User user = userService.getById(userId);
        if (user == null || user.getAvatar() == null || user.getAvatar().isEmpty()) {
            return R.ok(null);
        }
        String presignedUrl = avatarUrlService.getPresignedAvatarUrl(userId, user.getAvatar());
        return R.ok(presignedUrl);
    }
}