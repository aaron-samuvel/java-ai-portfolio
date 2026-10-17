package com.aaron.evalops.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class EvaluationExecutorConfiguration {
    @Bean(name = "evalOpsCaseExecutor")
    Executor evalOpsCaseExecutor(
            @Value("${evalops.parallelism:4}") int parallelism,
            @Value("${evalops.queue-capacity:64}") int queueCapacity) {
        if (parallelism < 1 || parallelism > 8) throw new IllegalArgumentException("EvalOps parallelism must be between 1 and 8");
        if (queueCapacity < 1 || queueCapacity > 128) throw new IllegalArgumentException("EvalOps queue capacity must be between 1 and 128");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(parallelism);
        executor.setMaxPoolSize(parallelism);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("evalops-case-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }
}
