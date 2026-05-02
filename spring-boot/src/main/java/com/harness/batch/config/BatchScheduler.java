package com.harness.batch.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ② Spring Batch: 스케줄러
 *
 * [실행 주기]
 * - 분석 Job: 매 시간 정각 실행
 * - 만료 토큰 정리: 매일 새벽 3시 (TokenService에서 관리)
 *
 * 비동기 실행(AsyncJobLauncher) 사용으로 스케줄러 블로킹 방지
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job analysisJob;

    /** 매 시간 정각에 분석 Job 실행 */
    @Scheduled(cron = "0 0 * * * *")
    public void runAnalysisJob() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("runAt", System.currentTimeMillis())  // 매번 새 파라미터로 새 실행
                    .toJobParameters();

            log.info("[BatchScheduler] analysisJob 시작");
            jobLauncher.run(analysisJob, params);

        } catch (Exception e) {
            log.error("[BatchScheduler] analysisJob 실행 실패: {}", e.getMessage(), e);
        }
    }
}
