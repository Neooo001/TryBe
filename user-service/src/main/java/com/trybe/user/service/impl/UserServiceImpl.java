package com.trybe.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trybe.common.utils.BeanUtils;
import com.trybe.user.config.JwtProperties;
import com.trybe.user.domain.po.User;
import com.trybe.user.domain.vo.UserLoginVO;
import com.trybe.user.domain.vo.UserProfileVO;
import com.trybe.user.domain.vo.WechatSessionVO;
import com.trybe.user.enums.UserStatus;
import com.trybe.user.mapper.UserMapper;
import com.trybe.user.service.AvatarUrlService;
import com.trybe.user.service.IUserService;
import com.trybe.user.service.WechatService;
import com.trybe.user.util.JwtTool;
import com.trybe.user.util.RefreshTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final WechatService wechatService;
    private final JwtTool jwtTool;
    private final JwtProperties jwtProperties;
    private final AvatarUrlService avatarUrlService;
    private final RefreshTokenUtil refreshTokenUtil;

    @Override
    @Transactional
    public UserLoginVO loginByWechat(String wxCode) {

        WechatSessionVO sessionVO = wechatService.getSession(wxCode);
        String openid = sessionVO.getOpenid();
        User user = lambdaQuery().eq(User::getOpenid, openid).one();
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setNickname("小Try");
            user.setStatus(UserStatus.NORMAL);
            save(user);
        }

        // 生成双Token
        Duration accessTtl = jwtProperties.getAccessTokenTTL();
        String accessToken = jwtTool.createToken(user.getId(), accessTtl);
        String refreshToken = refreshTokenUtil.generateToken();

        // RefreshToken 存入 Redis
        refreshTokenUtil.saveToken(refreshToken, user.getId(), openid);

        log.info("[Login] userId={} accessTokenExpiresIn={}s refreshTokenMask={}",
                user.getId(), accessTtl.getSeconds(), RefreshTokenUtil.maskToken(refreshToken));

        UserLoginVO userLoginVO = BeanUtils.copyBean(user, UserLoginVO.class);
        userLoginVO.setAccessToken(accessToken);
        userLoginVO.setRefreshToken(refreshToken);
        userLoginVO.setExpiresIn(accessTtl.getSeconds());

        if (StrUtil.isNotBlank(user.getAvatar())) {
            userLoginVO.setAvatar(avatarUrlService.getPresignedAvatarUrl(user.getId(), user.getAvatar()));
        }

        return userLoginVO;
    }

    @Override
    @Transactional
    public UserProfileVO updateProfile(Long userId, String avatar, String nickname, String bio, Integer gender) {
        User user = getById(userId);
        if (user == null) {
            return null;
        }
        boolean avatarChanged = false;
        if (StrUtil.isNotBlank(avatar)) {
            user.setAvatar(avatar);
            avatarChanged = true;
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

        if (avatarChanged) {
            avatarUrlService.invalidateCache(userId);
        }

        log.info("[updateProfile] 用户 {} 资料已更新", userId);
        UserProfileVO vo = BeanUtils.copyBean(user, UserProfileVO.class, (s, t) -> t.setUserId(s.getId()));
        if (StrUtil.isNotBlank(user.getAvatar())) {
            vo.setAvatar(avatarUrlService.getPresignedAvatarUrl(userId, user.getAvatar()));
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
        if (StrUtil.isNotBlank(user.getAvatar())) {
            vo.setAvatar(avatarUrlService.getPresignedAvatarUrl(userId, user.getAvatar()));
        }
        return vo;
    }
}