// AI 제목 생성 전용 스레드 풀 - 동시에 최대 3건만 처리해서 Claude API/외부 링크 fetch가 한꺼번에
// 몰리지 않게 함. 기본 @Async(이름 없는 SimpleAsyncTaskExecutor, 호출마다 새 스레드)는 그대로 두고
// 완전히 분리된 풀을 씀 (SlackDmNotificationService의 @Async에는 영향 없음)
package com.skalahub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    @Bean(name = "titleGenerationExecutor")
    public TaskExecutor titleGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("ai-title-");
        executor.initialize();
        return executor;
    }
}
