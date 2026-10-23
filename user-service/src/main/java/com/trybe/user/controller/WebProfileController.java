package com.trybe.user.controller;

import com.trybe.common.auth.RealmConstants;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.domain.R;
import com.trybe.common.exception.BadRequestException;
import com.trybe.common.exception.ForbiddenException;
import com.trybe.common.utils.UserContext;
import com.trybe.user.domain.dto.WebProfileUpdateDTO;
import com.trybe.user.domain.vo.UserProfileVO;
import com.trybe.user.service.IUserService;
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
 * Web 端账号资料接口 —— {@code /web/users/profile}（需登录，realm=web）
 *
 * <p><b>为什么单独一个控制器</b>：昵称 / 头像 / 简介从「创作者主页资料」里拆出来了。
 * 它们是「人」的属性（{@code app_user}），不是「创作者主页」的属性（{@code creator_profile}），
 * 拆开后小程序改一次昵称，后台与圈子作品署名会一起变。
 *
 * <p>封面图 / 创作方向 / 主页展示状态仍在 {@code /creator/profile}（创作者的专属资料）。
 *
 * <p>头像上传仍走 creator-service 的预签名直传（scene=AVATAR），拿到 objectKey 后回传本接口；
 * 小程序上传的头像（{@code avatars/{userId}/...}）与后台的（{@code creator/{userId}/profile/...}）
 * 同属 {@code trybe-avatar} 桶，仅路径约定不同，读时都能正确取 URL。
 *
 * @author TryBe Team
 */
@Api(tags = "Web 端账号资料接口")
@RestController
@RequestMapping("/web/users/profile")
@RequiredArgsConstructor
@Slf4j
@Validated
public class WebProfileController {

    private static final int NICKNAME_MAX_LENGTH = 32;

    private final IUserService userService;

    @ApiOperation("获取当前账号资料（昵称/头像/简介/性别/手机号）")
    @GetMapping
    public R<UserProfileVO> getProfile() {
        Long userId = requireWebRealmUser();
        UserProfileVO vo = userService.getUserProfile(userId);
        if (vo == null) {
            return R.error(ErrorCodeEnum.DATA_NOT_FOUND, "账号不存在");
        }
        return R.ok(vo);
    }

    @ApiOperation("更新当前账号资料（昵称/头像/简介/性别）")
    @PutMapping
    public R<UserProfileVO> updateProfile(@RequestBody @Validated WebProfileUpdateDTO dto) {
        Long userId = requireWebRealmUser();

        String nickname = dto.getNickname();
        if (nickname != null) {
            nickname = nickname.trim();
            if (nickname.isEmpty()) {
                throw new BadRequestException("昵称不能为空");
            }
            if (nickname.length() > NICKNAME_MAX_LENGTH) {
                throw new BadRequestException("昵称不能超过" + NICKNAME_MAX_LENGTH + "个字符");
            }
        }

        log.info("[webProfile] 更新账号资料 userId={} nicknameChanged={} avatarChanged={} bioChanged={}",
                userId, nickname != null, dto.getAvatarKey() != null, dto.getBio() != null);

        UserProfileVO vo = userService.updateProfile(
                userId, dto.getAvatarKey(), nickname, dto.getBio(), dto.getGender());
        return R.ok(vo);
    }

    /**
     * 取当前用户并校验 realm=web（纵深防御：网关已按 /web/** 校验，此处防内网直连绕过）
     */
    private Long requireWebRealmUser() {
        Long userId = UserContext.requireUser();
        if (!RealmConstants.WEB.equals(UserContext.getRealm())) {
            throw new ForbiddenException(ErrorCodeEnum.CROSS_REALM_FORBIDDEN, "无权访问该端接口");
        }
        return userId;
    }
}
