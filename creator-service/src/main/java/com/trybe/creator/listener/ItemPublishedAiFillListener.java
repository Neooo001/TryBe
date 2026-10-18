package com.trybe.creator.listener;

import com.trybe.creator.config.AsyncConfig;
import com.trybe.creator.domain.event.ItemPublishedEvent;
import com.trybe.creator.service.IGarmentInfoFillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 作品发布后自动解析商品信息
 *
 * <p><b>为什么用事务事件而不是在 publish() 里直接起线程</b>：
 * publish() 是有事务的，异步线程若在事务提交前就查库，会读到作品还没上架的旧状态
 * （甚至查不到）。{@link TransactionalEventListener} + {@link TransactionPhase#AFTER_COMMIT}
 * 保证「发布确实落库了才去解析」，把时序问题从「靠 sleep 碰运气」变成框架保证。
 *
 * <p>{@code fallbackExecution = true}：万一发布逻辑将来被放到没有事务的路径上，
 * 也不会静默不执行。
 *
 * <p>本类刻意只做「转发」，解析逻辑全在 {@link IGarmentInfoFillService}，
 * 便于单测与后续替换成 MQ 投递。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItemPublishedAiFillListener {

    private final IGarmentInfoFillService garmentInfoFillService;

    @Async(AsyncConfig.GARMENT_FILL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onItemPublished(ItemPublishedEvent event) {
        log.info("[garment-fill] 收到发布事件，开始后台解析商品信息 itemId={} creatorId={}",
                event.getItemId(), event.getCreatorId());
        garmentInfoFillService.fillFromItemImage(event.getCreatorId(), event.getItemId());
    }
}
