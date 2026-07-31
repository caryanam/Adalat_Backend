package com.whatsupmarketplacebackend.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async configuration for campaign processing workers.
 * Uses a dedicated thread pool to process campaigns without blocking the main request threads.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${async.core-pool-size:5}")
    private int corePoolSize;

    @Value("${async.max-pool-size:10}")
    private int maxPoolSize;

    @Value("${async.queue-capacity:50}")
    private int queueCapacity;

    @Bean(name = "campaignTaskExecutor")
    public Executor campaignTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("campaign-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
