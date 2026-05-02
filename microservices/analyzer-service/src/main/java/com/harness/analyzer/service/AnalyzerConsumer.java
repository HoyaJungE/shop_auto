package com.harness.analyzer.service;

import com.harness.events.AnalysisCompletedEvent;
import com.harness.events.DataCollectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ③ 마이크로서비스: analyzer-service Kafka 컨슈머
 *
 * [처리 흐름]
 * 1. collector-service가 발행한 DataCollectedEvent 수신
 * 2. Claude AI로 분석 위임 (① AI Agent 연동)
 * 3. 결과를 DB 저장
 * 4. AnalysisCompletedEvent 발행
 *
 * [신뢰성 설정]
 * - ackMode: MANUAL → 처리 완료 후 offset commit (메시지 유실 방지)
 * - concurrency: 3 → 파티션 3개에 대해 병렬 처리
 * - errorHandler: SeekToCurrentErrorHandler → 실패 시 재처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzerConsumer {

    private final AnalyzerService analyzerService;
    private final KafkaTemplate<String, AnalysisCompletedEvent> kafkaTemplate;

    @Value("${topics.analysis-completed}")
    private String analysisCompletedTopic;

    @KafkaListener(
            topics    = "${topics.data-collected}",
            groupId   = "${spring.kafka.consumer.group-id}",
            concurrency = "3"                           // 병렬 컨슈머 수 (파티션 수와 맞춤)
    )
    public void consume(DataCollectedEvent event,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        Acknowledgment ack) {
        log.info("[Analyzer] 이벤트 수신 dataId={}, dataType={}, partition={}",
                event.dataId(), event.dataType(), partition);

        try {
            // ① AI Agent에게 분석 위임
            AnalyzerService.AnalysisOutput output = analyzerService.analyze(event);

            // 분석 완료 이벤트 발행
            AnalysisCompletedEvent completedEvent = new AnalysisCompletedEvent(
                    UUID.randomUUID().toString(),
                    event.dataId(),
                    output.analysisType(),
                    output.summary(),
                    output.severity(),
                    LocalDateTime.now()
            );
            kafkaTemplate.send(analysisCompletedTopic, String.valueOf(event.dataId()), completedEvent);

            // ★ 처리 완료 후 수동 커밋 (MANUAL ack)
            ack.acknowledge();

        } catch (Exception e) {
            log.error("[Analyzer] 분석 실패 dataId={}: {}", event.dataId(), e.getMessage(), e);
            // ack 하지 않으면 재처리됨 → 의도적으로 ack 생략 (재시도 유도)
            // 무한루프 방지: application.yml에 max-failures 설정 권장
        }
    }
}
