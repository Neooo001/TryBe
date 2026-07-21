package com.trybe.user.service.impl;

import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BizIllegalException;
import com.trybe.user.config.WechatProperties;
import com.trybe.user.domain.vo.WechatSessionVO;
import com.trybe.user.service.WechatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class WechatServiceImpl implements WechatService {

    private final WechatProperties wechatProperties;
    private final RestTemplate restTemplate;

    @Override
    public WechatSessionVO getSession(String wxCode) {
        String url = String.format("https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                wechatProperties.getAppId(),
                wechatProperties.getAppSecret(),
                wxCode);
        log.info("请求微信登录接口: {}", url);
        WechatSessionVO sessionVO = restTemplate.getForObject(url, WechatSessionVO.class);
        if (sessionVO == null || sessionVO.getErrcode() != null && sessionVO.getErrcode() != 0) {
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "第三方微信登录授权失败");
        }
        log.info("微信登录结果: {}", sessionVO);
        return sessionVO;
    }
}
