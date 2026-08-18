package com.trybe.api.client;

import com.trybe.api.dto.BodyProfileDTO;
import io.swagger.annotations.ApiOperation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 身形档案 Feign 客户端 — 定义在 trybe-api 模块
 *
 * 服务提供方：body-profile-service
 * 服务调用方：其他依赖 trybe-api 的微服务
 */
@FeignClient(value = "body-profile-service")
public interface BodyProfileClient {

    /**
     * 获取用户档案详情（含关联素材）
     *
     * @param userId 用户ID
     * @return 档案 DTO，不存在返回 null
     */
    @GetMapping("/profiles/feign/mine")
    BodyProfileDTO getMyProfile(@RequestParam("userId") Long userId);
}
