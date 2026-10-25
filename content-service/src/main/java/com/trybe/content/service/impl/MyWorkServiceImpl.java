package com.trybe.content.service.impl;

import cn.hutool.core.util.StrUtil;
import com.trybe.api.client.CreatorClient;
import com.trybe.api.dto.MyWorkBriefDTO;
import com.trybe.api.dto.PageResult;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.domain.R;
import com.trybe.common.exception.BadRequestException;
import com.trybe.common.exception.BizIllegalException;
import com.trybe.content.service.IMyWorkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 小程序「我的作品」服务实现
 *
 * <p>本服务不碰数据库、不碰 OSS：作品数据全部由 creator-service 出口，
 * 这里只做「参数收敛 + 错误翻译」两件事。
 *
 * <p><b>错误翻译原则</b>：
 * <ul>
 *   <li>上游说「数据不存在」（402）→ 原样告诉用户作品不在了（可能已被网页后台删除）</li>
 *   <li>上游说业务规则不满足（如「仅已下架的物品可上架」）→ <b>保留上游文案</b>。
 *       这种文案对用户是有用的（说明当前状态点错了按钮），替换成「操作失败」只会让人更懵</li>
 *   <li>上游超时 / 反序列化等传输层异常 → 统一「操作失败，请稍后重试」，
 *       绝不能当成「操作成功」，否则界面显示了新状态但库里没改</li>
 * </ul>
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MyWorkServiceImpl implements IMyWorkService {

    /** 与后端全局成功码一致（R.ok 的 code） */
    private static final int SUCCESS_CODE = 200;

    private static final String STATUS_ON_SHELF = "ON_SHELF";
    private static final String STATUS_OFF_SHELF = "OFF_SHELF";

    private final CreatorClient creatorClient;

    @Override
    public PageResult<MyWorkBriefDTO> pageMyWorks(String status, Integer pageNo, Integer pageSize) {
        String safeStatus = normalizeStatus(status);
        int safePageNo = (pageNo == null || pageNo < 1) ? 1 : pageNo;
        // 上限 50：列表是「我的作品」，不需要一次拉几百条；也避免翻页参数被当成分页攻击面
        int safePageSize = (pageSize == null || pageSize < 1) ? 20 : Math.min(pageSize, 50);

        R<PageResult<MyWorkBriefDTO>> result;
        try {
            result = creatorClient.pageMyWorks(safeStatus, safePageNo, safePageSize);
        } catch (Exception e) {
            log.error("[my-work] 查询我的作品失败 status={} pageNo={}", safeStatus, safePageNo, e);
            throw new BizIllegalException("作品列表加载失败，请稍后重试", e);
        }
        if (result == null) {
            log.warn("[my-work] 我的作品上游返回 null status={}", safeStatus);
            throw new BizIllegalException("作品列表加载失败，请稍后重试");
        }
        if (!isSuccess(result)) {
            log.warn("[my-work] 我的作品上游返回异常 status={} code={} msg={}",
                    safeStatus, result.getCode(), result.getMsg());
            // 列表查不出来就是查不出来，不要假装「你还没有作品」
            throw new BizIllegalException(StrUtil.blankToDefault(result.getMsg(), "作品列表加载失败，请稍后重试"));
        }
        return result.getData() == null ? PageResult.empty() : result.getData();
    }

    @Override
    public void onShelf(Long id) {
        requireId(id);
        R<Void> result;
        try {
            result = creatorClient.onShelfMyWork(id);
        } catch (Exception e) {
            log.error("[my-work] 上架失败 id={}", id, e);
            throw new BizIllegalException("上架失败，请稍后重试", e);
        }
        assertWriteSucceeded(result, id, "上架");
    }

    @Override
    public void offShelf(Long id) {
        requireId(id);
        R<Void> result;
        try {
            result = creatorClient.offShelfMyWork(id);
        } catch (Exception e) {
            log.error("[my-work] 下架失败 id={}", id, e);
            throw new BizIllegalException("下架失败，请稍后重试", e);
        }
        assertWriteSucceeded(result, id, "下架");
    }

    // ==================== 私有方法 ====================

    /**
     * 状态白名单：只放已上架 / 已下架，空值兜底「已上架」
     *
     * <p>在发 RPC 之前先挡掉非法值，省一次无意义的远程调用；
     * creator-service 侧还有第二道同样的校验（纵深防御，任一侧被绕过都拦得住）。
     */
    private String normalizeStatus(String status) {
        if (StrUtil.isBlank(status)) {
            return STATUS_ON_SHELF;
        }
        String trimmed = status.trim().toUpperCase();
        if (STATUS_ON_SHELF.equals(trimmed) || STATUS_OFF_SHELF.equals(trimmed)) {
            return trimmed;
        }
        log.warn("[my-work] 非法状态参数 status={}", status);
        throw new BadRequestException(ErrorCodeEnum.PARAM_ERROR, "仅支持查看已上架 / 已下架的作品");
    }

    private void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException(ErrorCodeEnum.PARAM_ERROR, "作品ID不能为空");
        }
    }

    /**
     * 写操作的统一收口：失败必须抛异常
     *
     * <p>这里刻意<b>不做</b>「失败也返回成功」的宽容处理 —— 上下架是状态变更，
     * 前端会据此把按钮从「下架」翻成「上架」。如果实际没改成功却回了成功，
     * 用户会看到一件已经下架的作品继续挂在圈子里。
     */
    private void assertWriteSucceeded(R<Void> result, Long id, String action) {
        if (result == null) {
            log.warn("[my-work] {} 上游返回 null id={}", action, id);
            throw new BizIllegalException(action + "失败，请稍后重试");
        }
        if (result.getCode() == ErrorCodeEnum.DATA_NOT_FOUND.getCode()) {
            throw new BizIllegalException(ErrorCodeEnum.DATA_NOT_FOUND, "作品不存在或不属于你");
        }
        if (!isSuccess(result)) {
            log.warn("[my-work] {} 上游拒绝 id={} code={} msg={}", action, id, result.getCode(), result.getMsg());
            throw new BizIllegalException(StrUtil.blankToDefault(result.getMsg(), action + "失败，请稍后重试"));
        }
    }

    private boolean isSuccess(R<?> result) {
        return result.getCode() == SUCCESS_CODE;
    }
}
