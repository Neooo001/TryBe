package com.trybe.api.client;

import com.trybe.api.dto.BatchMaterialResult;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * <p>
 * 素材中心 Feign 客户端 — 定义在 trybe-api 模块
 * </p>
 *
 * <p>服务提供方：material-service<br>
 * 服务调用方：body-profile-service 等依赖 trybe-api 的微服务
 *
 * <p>方法对齐现有 {@link UserClient} 写法：
 * <ul>
 *   <li>不使用 @RequestBody + 自定义DTO，统一用 @RequestParam</li>
 *   <li>返回值直接使用泛型，不包裹 R</li>
 * </ul>
 */
@FeignClient(value = "material-service")
public interface MaterialClient {

    /**
     * 批量校验素材有效性
     * <p>
     * 校验通过的条件：素材存在 + 归属当前用户 + status=PASSED
     *
     * @param userId       当前用户ID
     * @param materialIds  素材ID列表（逗号分隔）
     * @return 批量校验结果（有效素材列表 + 无效ID列表）
     */
    @GetMapping("/materials/feign/validate")
    BatchMaterialResult validateMaterials(
            @RequestParam("userId") Long userId,
            @RequestParam("materialIds") List<Long> materialIds);

    /**
     * 批量获取素材临时访问链接
     * <p>
     * 仅返回 status=PASSED 且归属当前用户的素材的预签名URL
     *
     * @param userId      当前用户ID
     * @param materialIds 素材ID列表（逗号分隔）
     * @return 批量结果（含临时访问URL的素材列表）
     */
    @GetMapping("/materials/feign/access-urls")
    BatchMaterialResult getMaterialAccessUrls(
            @RequestParam("userId") Long userId,
            @RequestParam("materialIds") List<Long> materialIds);

    /**
     * 删除素材（软删 material + 删 OSS 文件），供档案服务级联清理孤儿素材
     *
     * @param userId     素材归属用户ID
     * @param materialId 素材ID
     */
    @DeleteMapping("/materials/feign/{materialId}")
    void deleteMaterial(@PathVariable("materialId") Long materialId,
                        @RequestParam("userId") Long userId);
}
