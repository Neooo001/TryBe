package com.trybe.api.client;

import com.trybe.api.dto.PageResult;
import com.trybe.api.dto.WorkBriefDTO;
import com.trybe.api.dto.WorkDetailDTO;
import com.trybe.api.dto.WorkQuery;
import com.trybe.common.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

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
}
