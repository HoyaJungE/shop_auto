package com.harness.batch.writer;

import com.harness.batch.processor.AnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * ② Spring Batch: ItemWriter
 *
 * 분석 결과를 DB에 저장하고 원본 데이터 status를 업데이트한다.
 * JdbcTemplate 배치 업데이트로 N+1 없이 한번에 처리.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResultWriter implements ItemWriter<AnalysisResult> {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void write(Chunk<? extends AnalysisResult> chunk) throws Exception {
        int size = chunk.size();
        log.info("[Batch] Writing {} results", size);

        // 분석 결과 저장
        jdbcTemplate.batchUpdate(
                "INSERT INTO analysis_result (analysis_key, result, created_at) VALUES (?, ?::jsonb, NOW()) ON CONFLICT DO NOTHING",
                chunk.getItems(),
                size,
                (ps, item) -> {
                    ps.setString(1, "batch-" + item.sourceId());
                    ps.setString(2, item.result() != null ? item.result() : "{}");
                }
        );

        // 원본 데이터 상태 업데이트
        jdbcTemplate.batchUpdate(
                "UPDATE collected_data SET status = ?, processed_at = NOW() WHERE id = ?",
                chunk.getItems(),
                size,
                (ps, item) -> {
                    ps.setString(1, item.status());
                    ps.setLong(2, item.sourceId());
                }
        );

        log.info("[Batch] {} items written successfully", size);
    }
}
