package com.trybe.wardrobe.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * <p>
 * 衣柜模块异步执行器
 * </p>
 *
 * <p>用途：MQ 投递失败（中间件不可用、网络抖动、队列被删等）时的<b>本地降级</b>。
 * 此时直接在本机线程池执行识别流水线，保证「导入衣服」不因 MQ 单点故障而完全不可用，
 * 同时上传接口依旧保持毫秒级返回，不阻塞小程序请求线程。
 *
 * <p>拒绝策略选 CallerRunsPolicy：队列打满时由提交线程执行，
 * 宁可让这一次请求慢一点，也不静默丢弃用户的上传任务。
 */
@Configuration
public class WardrobeAsyncConfig {

    @Bean("wardrobeImportExecutor")
    public ThreadPoolTaskExecutor wardrobeImportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("wardrobe-import-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
