package com.harness.events;

import java.time.LocalDateTime;

/**
 * ③ 마이크로서비스 공유 이벤트 (Kafka 메시지 페이로드)
 *
 * [이벤트 흐름]
 * collector-service → [Kafka: harness.data.collected] → analyzer-service
 *
 * 모든 서비스가 이 모듈에 의존 (shared-events JAR)
 * 주의: 이벤트 필드 변경 시 하위 호환성 유지 필요
 */
public record DataCollectedEvent(
        String eventId,          // UUID, 중복 처리 방지용
        Long dataId,             // collected_data 테이블 PK
        String dataType,         // "user_input" | "log" | "crawl"
        String clientId,         // Windows 클라이언트 식별자
        LocalDateTime occurredAt
) {}
