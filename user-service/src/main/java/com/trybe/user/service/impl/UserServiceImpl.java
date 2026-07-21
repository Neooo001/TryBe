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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final WechatService wechatService;
    private final JwtTool jwtTool;
    private final JwtProperties jwtProperties;
    private final AvatarUrlService avatarUrlService;

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
        String token = jwtTool.createToken(user.getId(), jwtProperties.getTokenTTL());
        log.info("token: {}", token);
        UserLoginVO userLoginVO = BeanUtils.copyBean(user, UserLoginVO.class);
        userLoginVO.setToken(token);

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