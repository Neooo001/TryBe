package com.trybe.common.service;

import com.aliyun.sdk.service.dypnsapi20170525.AsyncClient;
import com.aliyun.sdk.service.dypnsapi20170525.models.CheckSmsVerifyCodeRequest;
import com.aliyun.sdk.service.dypnsapi20170525.models.CheckSmsVerifyCodeResponse;
import com.aliyun.sdk.service.dypnsapi20170525.models.CheckSmsVerifyCodeResponseBody;
import com.aliyun.sdk.service.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.sdk.service.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.trybe.common.config.SmsProperties;
import com.trybe.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {

    private static final String LIMIT_PREFIX = "sms:limit:";
    private static final long SEND_INTERVAL_SECONDS = 60;

    private final AsyncClient smsAsyncClient;
    private final SmsProperties smsProperties;
    private final StringRedisTemplate stringRedisTemplate;

    public void sendVerifyCode(String phone) {
        String limitKey = LIMIT_PREFIX + phone;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(limitKey))) {
            Long ttl = stringRedisTemplate.getExpire(limitKey);
            throw new BadRequestException("操作过于频繁，请" + (ttl != null && ttl > 0 ? ttl : 60) + "秒后再试");
        }

        SendSmsVerifyCodeRequest request = SendSmsVerifyCodeRequest.builder()
                .signName(smsProperties.getSignName())
                .templateCode(smsProperties.getTemplateCode())
                .phoneNumber(phone)
                .templateParam(smsProperties.getTemplateParam())
                .build();

        try {
            CompletableFuture<SendSmsVerifyCodeResponse> future = smsAsyncClient.sendSmsVerifyCode(request);
            SendSmsVerifyCodeResponse response = future.get(5, TimeUnit.SECONDS);

            if (!"OK".equals(response.getBody().getCode())) {
                log.error("[SMS] 发送验证码失败 phone={} code={} msg={}",
                        maskPhone(phone), response.getBody().getCode(), response.getBody().getMessage());
                throw new BadRequestException("验证码发送失败，请稍后再试");
            }

            stringRedisTemplate.opsForValue().set(limitKey, "1", SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);
            log.info("[SMS] 验证码发送成功 phone={} requestId={}", maskPhone(phone),
                    response.getBody().getRequestId());
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("[SMS] 调用阿里云短信接口异常 phone={}", maskPhone(phone), e);
            throw new BadRequestException("验证码发送失败，请稍后再试");
        }
    }

    public boolean verifyCode(String phone, String code) {
        CheckSmsVerifyCodeRequest request = CheckSmsVerifyCodeRequest.builder()
                .phoneNumber(phone)
                .verifyCode(code)
                .schemeName(smsProperties.getSchemeName())
                .build();

        try {
            CompletableFuture<CheckSmsVerifyCodeResponse> future = smsAsyncClient.checkSmsVerifyCode(request);
            CheckSmsVerifyCodeResponse response = future.get(5, TimeUnit.SECONDS);
            String respCode = response.getBody().getCode();
            CheckSmsVerifyCodeResponseBody.Model model = response.getBody().getModel();

            if ("OK".equals(respCode) && model != null && "PASS".equals(model.getVerifyResult())) {
                log.info("[SMS] 验证码校验通过 phone={}", maskPhone(phone));
                return true;
            }

            String result = model != null ? model.getVerifyResult() : "null";
            log.warn("[SMS] 验证码校验失败 phone={} code={} result={}", maskPhone(phone), respCode, result);
            return false;
        } catch (Exception e) {
            log.error("[SMS] 验证码校验异常 phone={}", maskPhone(phone), e);
            return false;
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}