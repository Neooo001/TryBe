package com.trybe.creator.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池
 *
 * <p>当前唯一用途：作品发布后的「AI 商品信息回填」（见
 * {@code ItemPublishedAiFillListener}）。
 *
 * <p><b>为什么必须独立线程池</b>：大模型调用 5~30s，绝不能挂在发布请求的线程上；
 * 而且它要等发布事务提交后才能读到作品数据，天然属于「事后任务」。
 *
 * <p><b>为什么用 CallerRunsPolicy</b>：队列打满时把任务交回调用线程执行 ——
 * 对 AFTER_COMMIT 事务同步回调而言，调用线程就是业务线程，会短暂阻塞发布响应，
 * 但<b>不会丢任务</b>。毕设/单机场景下「慢一点」远比「静默丢失解析」可接受。
 * 若将来量大，应改投 MQ（wardrobe-service 的导入链路就是这么做的）。
 *
 * <p><b>优雅停机</b>：等在跑的大模型调用结束再退出，避免解析结果写不回去。
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /** 商品信息回填线程池的 Bean 名，{@code @Async} 按名引用 */
    public static final String GARMENT_FILL_EXECUTOR = "garmentFillExecutor";

    @Bean(GARMENT_FILL_EXECUTOR)
    public ThreadPoolTaskExecutor garmentFillExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("garment-fill-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
