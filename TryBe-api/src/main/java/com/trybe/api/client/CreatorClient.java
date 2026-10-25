package com.trybe.api.client;

import com.trybe.api.dto.MyWorkBriefDTO;
import com.trybe.api.dto.PageResult;
import com.trybe.api.dto.WorkBriefDTO;
import com.trybe.api.dto.WorkDetailDTO;
import com.trybe.api.dto.WorkQuery;
import com.trybe.common.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * creator-service 的内部只读客户端（服务间调用，<b>不经过网关</b>）
 *
 * <p>为什么要有这一层，而不是让 content-service 直接读 {@code creator_item} 表：
 * creator-service 是作品数据的属主服务（唯一持有 MyBatis mapper 与 OSS 签名能力），
 * C 端只能经它出口取数；否则两张表被两个服务共享读写，改一次表结构要同步改两处，
 * 谁都能写 = 没人负责。
 *
 * <p>路径为 {@code /internal/creator/**}：<b>网关没有注册该前缀的路由</b>，
 * 外部请求在这里根本进不来（404），只能由注册中心内部直连调用，天然隔离。
 */
@FeignClient(value = "creator-service", path = "/internal/creator")
public interface CreatorClient {

    /**
     * 分页查询已上架的公开作品
     *
     * <p>用 {@link PageResult} 而不是 common 的 {@code PageDTO}：本模块不引 MyBatis-Plus，
     * 反序列化 {@code PageDTO} 会因找不到 MP 的 {@code Page} 类而失败。
     */
    @GetMapping("/works")
    R<PageResult<WorkBriefDTO>> pagePublicWorks(@SpringQueryMap WorkQuery query);

    /**
     * 单个已上架作品的公开详情
     *
     * <p>作品不存在 / 未上架时上游返回 {@code code=402}（DATA_NOT_FOUND），
     * 由 content-service 转成对外的「作品不存在或已下架」。
     */
    @GetMapping("/works/{id}")
    R<WorkDetailDTO> getWorkDetail(@PathVariable("id") Long id);

    /**
     * 作品分区字典（顺序即展示顺序）
     */
    @GetMapping("/works/zones")
    R<List<String>> listZones();

    // ==================== 小程序「我的作品」（本人看 + 上下架） ====================

    /**
     * 分页查询「我」的作品（仅已上架 / 已下架，草稿不出现在 C 端）
     *
     * <p><b>为什么不复用 {@link #pagePublicWorks}</b>：那是「任意人的已上架作品」，
     * 按发布时间排序、带创作者资料；这里是「我自己的、含已下架」，按创建时间倒序。
     * 两个口径混用会让「为什么我下架的作品在圈子里还能看到」变成必然。
     *
     * <p>身份不在这里传：{@code DefaultFeignConfig} 的拦截器会把当前 UserContext 的
     * userId 写进 {@code user-info} 头，creator-service 侧用 {@code UserContext.requireUser()} 取回。
     *
     * @param status   ON_SHELF / OFF_SHELF（由 content-service 做过白名单校验）
     * @param pageNo   页码，从 1 开始
     * @param pageSize 每页数量
     */
    @GetMapping("/my-works")
    R<PageResult<MyWorkBriefDTO>> pageMyWorks(@RequestParam("status") String status,
                                             @RequestParam("pageNo") Integer pageNo,
                                             @RequestParam("pageSize") Integer pageSize);

    /**
     * 上架我的作品（OFF_SHELF → ON_SHELF）
     */
    @PostMapping("/my-works/{id}/on-shelf")
    R<Void> onShelfMyWork(@PathVariable("id") Long id);

    /**
     * 下架我的作品（ON_SHELF → OFF_SHELF）
     */
    @PostMapping("/my-works/{id}/off-shelf")
    R<Void> offShelfMyWork(@PathVariable("id") Long id);
}
