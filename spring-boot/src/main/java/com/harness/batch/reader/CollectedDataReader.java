package com.harness.batch.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.persistence.EntityManagerFactory;
import java.util.Map;

/**
 * ② Spring Batch: ItemReader
 *
 * DB에서 status='RAW' 인 collected_data를 페이지 단위로 읽어온다.
 * JpaPagingItemReader → 대용량 처리에 적합 (커서 방식 X, 페이지 쿼리 방식)
 *
 * 실제 도메인 Entity로 교체 시:
 *   - <Map<String, Object>> → <CollectedData> 로 변경
 *   - JPQL 쿼리도 맞게 수정
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CollectedDataReader {

    private final EntityManagerFactory entityManagerFactory;

    @Bean
    public JpaPagingItemReader<Object[]> rawDataReader() {
        return new JpaPagingItemReaderBuilder<Object[]>()
                .name("rawDataReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("""
                        SELECT cd.id, cd.payload, cd.dataType
                        FROM CollectedData cd
                        WHERE cd.status = 'RAW'
                        ORDER BY cd.collectedAt ASC
                        """)
                .pageSize(100)       // 한 번에 100건씩 처리 (Chunk size와 맞출 것)
                .build();
    }
}
