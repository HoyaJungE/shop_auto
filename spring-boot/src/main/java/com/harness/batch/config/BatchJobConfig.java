package com.harness.batch.config;

import com.harness.batch.processor.AnalysisProcessor;
import com.harness.batch.processor.AnalysisResult;
import com.harness.batch.writer.ResultWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * ② Spring Batch: Job/Step 구성
 *
 * [Job 구조]
 * analysisJob
 *   └── analysisStep (Chunk 방식: Reader → Processor → Writer)
 *         ├── chunk size: 100 (한 트랜잭션에 100건)
 *         ├── faultTolerant: API 오류 시 skip (최대 10건)
 *         └── retry: 네트워크 오류 시 3회 재시도
 *
 * [스케줄]
 * BatchScheduler에서 Cron으로 주기적으로 실행
 * 수동 실행: POST /api/v1/admin/batch/analysis/run
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class BatchJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JpaPagingItemReader<Object[]> rawDataReader;
    private final AnalysisProcessor analysisProcessor;
    private final ResultWriter resultWriter;

    @Bean
    public Job analysisJob() {
        return new JobBuilder("analysisJob", jobRepository)
                .incrementer(new RunIdIncrementer())   // 매번 새 JobInstance 생성
                .start(analysisStep())
                .build();
    }

    @Bean
    public Step analysisStep() {
        return new StepBuilder("analysisStep", jobRepository)
                .<Object[], AnalysisResult>chunk(100, transactionManager)
                .reader(rawDataReader)
                .processor(analysisProcessor)
                .writer(resultWriter)
                // ── 내결함성 설정 ─────────────────────────────────────────
                .faultTolerant()
                .skipLimit(10)                         // 최대 10건 스킵 후 계속
                .skip(Exception.class)                 // 모든 예외 스킵 가능
                .retryLimit(3)                         // 재시도 3회
                .retry(org.springframework.web.client.ResourceAccessException.class)
                .build();
    }
}
