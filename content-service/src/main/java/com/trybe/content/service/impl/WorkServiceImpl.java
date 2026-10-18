package com.trybe.content.service.impl;

import com.trybe.api.client.CreatorClient;
import com.trybe.api.dto.PageResult;
import com.trybe.api.dto.WorkBriefDTO;
import com.trybe.api.dto.WorkDetailDTO;
import com.trybe.api.dto.WorkQuery;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.domain.R;
import com.trybe.common.exception.BizIllegalException;
import com.trybe.content.service.IWorkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * C 端作品浏览服务实现
 *
 * <p><b>降级策略：上游不可用时不抛异常，返回空页 / 空字典。</b>
 * 浏览页挂掉应该是「暂时没有内容」的空态，而不是整页报错——
 * 作品浏览是只读展示，用户刷新一次即可恢复，没必要拿 5xx 打断阅读。
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkServiceImpl implements IWorkService {

    /** 与后端全局成功码一致（R.ok 的 code） */
    private static final int SUCCESS_CODE = 200;

    private final CreatorClient creatorClient;

    @Override
    public PageResult<WorkBriefDTO> pageWorks(WorkQuery query) {
        WorkQuery safeQuery = (query == null ? new WorkQuery() : query).normalize();
        try {
            R<PageResult<WorkBriefDTO>> result = creatorClient.pagePublicWorks(safeQuery);
            if (result == null || result.getCode() != SUCCESS_CODE || result.getData() == null) {
                log.warn("[work] 上游返回异常 result={}", result);
                return PageResult.empty();
            }
            return result.getData();
        } catch (Exception e) {
            log.error("[work] 查询公开作品失败 zone={} pageNo={}",
                    safeQuery.getZone(), safeQuery.getPageNo(), e);
            return PageResult.empty();
        }
    }

    @Override
    public List<String> listZones() {
        try {
            R<List<String>> result = creatorClient.listZones();
            if (result == null || result.getCode() != SUCCESS_CODE || result.getData() == null) {
                log.warn("[work] 上游分区字典返回异常 result={}", result);
                return Collections.emptyList();
            }
            return result.getData();
        } catch (Exception e) {
            log.error("[work] 查询作品分区字典失败", e);
            return Collections.emptyList();
        }
    }

    @Override
    public WorkDetailDTO getWorkDetail(Long id) {
        R<WorkDetailDTO> result;
        try {
            result = creatorClient.getWorkDetail(id);
        } catch (Exception e) {
            // 传输层异常（超时 / 断路器 / 反序列化）不是「作品不存在」，不能装作查无此作
            log.error("[work] 查询作品详情失败 id={}", id, e);
            throw new BizIllegalException("作品详情加载失败，请稍后重试", e);
        }
        if (result == null) {
            log.warn("[work] 作品详情上游返回 null id={}", id);
            throw new BizIllegalException("作品详情加载失败，请稍后重试");
        }
        if (result.getCode() == ErrorCodeEnum.DATA_NOT_FOUND.getCode()) {
            throw new BizIllegalException(ErrorCodeEnum.DATA_NOT_FOUND, "作品不存在或已下架");
        }
        if (result.getCode() != SUCCESS_CODE || result.getData() == null) {
            log.warn("[work] 作品详情上游返回异常 id={} result={}", id, result);
            throw new BizIllegalException("作品详情加载失败，请稍后重试");
        }
        return result.getData();
    }
}
