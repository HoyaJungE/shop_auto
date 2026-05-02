package com.harness.collector.service;

import com.harness.events.DataCollectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * ③ 마이크로서비스: collector-service
 *
 * 역할: Windows 클라이언트로부터 데이터를 수신하고 DB에 저장 후
 *       Kafka로 DataCollectedEvent를 발행한다.
 *
 * [Kafka 발행 패턴]
 * - CompletableFuture로 비동기 확인
 * - 실패 시 로그 + 재시도 (Kafka producer retry 설정)
 * - 멱등성: enable.idempotence=true
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectorService {

    private final KafkaTemplate<String, DataCollectedEvent> kafkaTemplate;

    @Value("${topics.data-collected}")
    private String dataCollectedTopic;

    /**
     * 데이터 수집 완료 후 이벤트 발행
     * @param dataId DB에 저장된 collected_data.id
     * @param dataType "user_input" | "log" | "crawl"
     * @param clientId Windows 클라이언트 식별자
     */
    public void publishDataCollected(Long dataId, String dataType, String clientId) {
        DataCollectedEvent event = new DataCollectedEvent(
                UUID.randomUUID().toString(),
                dataId,
                dataType,
                clientId,
                LocalDateTime.now()
        );

        CompletableFuture<SendResult<String, DataCollectedEvent>> future =
                kafkaTemplate.send(dataCollectedTopic, clientId, event);  // clientId를 key로 → 같은 클라이언트 메시지 순서 보장

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[Collector] Kafka 발행 실패 dataId={}: {}", dataId, ex.getMessage());
                // TODO: DLQ(Dead Letter Queue) 또는 DB 재시도 큐로 이동
            } else {
                log.debug("[Collector] Kafka 발행 성공 dataId={}, offset={}",
                        dataId, result.getRecordMetadata().offset());
            }
        });
    }
}
