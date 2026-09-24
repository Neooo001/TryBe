package com.trybe.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trybe.common.service.OssService;
import com.trybe.common.utils.BeanUtils;
import com.trybe.user.config.WechatProperties;
import com.trybe.user.domain.po.User;
import com.trybe.user.domain.vo.UserLoginVO;
import com.trybe.user.domain.vo.UserProfileVO;
import com.trybe.user.domain.vo.WechatSessionVO;
import com.trybe.user.enums.IdentityType;
import com.trybe.user.mapper.UserMapper;
import com.trybe.user.service.IUserService;
import com.trybe.user.service.UserIdentityService;
import com.trybe.user.service.WechatService;
import com.trybe.user.support.TokenIssuer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户服务实现（统一账号体系 v2）。
 *
 * <p>微信登录改为「按凭据找人」：以 {@code WECHAT_OPENID + app_id} 解析
 * {@code user_identity}，未命中则建号（{@code phone_state=NEVER_BOUND}）。
 * 登录 VO 回传 {@code phoneState}，供小程序区分硬拦截/软提示。
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final WechatService wechatService;
    private final WechatProperties wechatProperties;
    private final UserIdentityService userIdentityService;
    private final TokenIssuer tokenIssuer;
    private final OssService ossService;

    /**
     * 微信小程序登录。
     *
     * <p>方法内含外部交互（微信 jscode2session），<b>不加 {@code @Transactional}</b>；
     * 建号（纯 DB）委托 {@link UserIdentityService#registerWechatUserInTx(String, String)}。
     */
    @Override
    public UserLoginVO loginByWechat(String wxCode) {
        WechatSessionVO sessionVO = wechatService.getSession(wxCode);
        String openid = sessionVO.getOpenid();
        String appId = wechatProperties.getAppId();

        Long userId = userIdentityService.resolveUserIdByIdentity(IdentityType.WECHAT_OPENID, appId, openid);
        if (userId == null) {
            // 首次登录：建号 + WECHAT_OPENID 凭据
            userId = userIdentityService.registerWechatUserInTx(appId, openid);
            log.info("[Login] 微信首登建号 userId={}", userId);
        }

        User user = getById(userId);
        if (user == null) {
            user = new User().setId(userId);
        }

        // 统一令牌下发（内部 nullToEmpty(openid)，修复 P5）
        TokenIssuer.TokenPair token = tokenIssuer.issue(userId, openid);

        UserLoginVO vo = new UserLoginVO();
        vo.setUserId(userId);
        vo.setNickname(user.getNickname());
        vo.setBio(user.getBio());
        vo.setGender(user.getGender());
        vo.setRole(user.getRole());
        vo.setOpenid(openid);
        vo.setPhone(userIdentityService.getCurrentPhone(userId));
        vo.setPhoneState(user.getPhoneState());
        vo.setAccessToken(token.getAccessToken());
        vo.setRefreshToken(token.getRefreshToken());
        vo.setExpiresIn(token.getExpiresIn());

        if (StrUtil.isNotBlank(user.getAvatar())) {
            vo.setAvatar(ossService.getPublicUrl(user.getAvatar()));
        }

        log.info("[Login] 微信登录成功 userId={} accessTokenExpiresIn={}s",
                userId, token.getExpiresIn());
        return vo;
    }

    @Override
    @Transactional
    public UserProfileVO updateProfile(Long userId, String avatar, String nickname, String bio, Integer gender) {
        User user = getById(userId);
        if (user == null) {
            return null;
        }
        if (StrUtil.isNotBlank(avatar)) {
            user.setAvatar(avatar);
        }
        if (StrUtil.isNotBlank(nickname)) {
            user.setNickname(nickname);
        }
        if (bio != null) {
            user.setBio(bio);
        }
        if (gender != null) {
            user.setGender(gender);
        }
        user.setUpdateTime(LocalDateTime.now());
        updateById(user);

        log.info("[updateProfile] 用户 {} 资料已更新", userId);
        UserProfileVO vo = BeanUtils.copyBean(user, UserProfileVO.class, (s, t) -> t.setUserId(s.getId()));
        // 手机号从 user_identity 取（user 表已无 phone 列）
        vo.setPhone(userIdentityService.getCurrentPhone(userId));
        if (StrUtil.isNotBlank(user.getAvatar())) {
            vo.setAvatar(ossService.getPublicUrl(user.getAvatar()));
        }
        return vo;
    }

    @Override
    public UserProfileVO getUserProfile(Long userId) {
        User user = getById(userId);
        if (user == null) {
            return null;
        }
        UserProfileVO vo = BeanUtils.copyBean(user, UserProfileVO.class, (s, t) -> t.setUserId(s.getId()));
        // 手机号从 user_identity 取（user 表已无 phone 列）
        vo.setPhone(userIdentityService.getCurrentPhone(userId));
        if (StrUtil.isNotBlank(user.getAvatar())) {
            vo.setAvatar(ossService.getPublicUrl(user.getAvatar()));
        }
        return vo;
    }
}
