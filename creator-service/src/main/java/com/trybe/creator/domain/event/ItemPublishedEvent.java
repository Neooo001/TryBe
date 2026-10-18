package com.trybe.creator.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 作品已发布事件
 *
 * <p>由 {@code CreatorItemServiceImpl#publish} 在发布事务内发布，
 * 监听方（{@code ItemPublishedAiFillListener}）在<b>事务提交后</b>才消费，
 * 避免异步线程读到还没提交的作品数据。
 *
 * <p>刻意不写成 record：本项目父 POM 的编译级别是 Java 11，record 需要 16+。
 */
@Getter
@AllArgsConstructor
public class ItemPublishedEvent {

    /** 创作者用户ID */
    private final Long creatorId;

    /** 作品ID（creator_item.id） */
    private final Long itemId;
}
