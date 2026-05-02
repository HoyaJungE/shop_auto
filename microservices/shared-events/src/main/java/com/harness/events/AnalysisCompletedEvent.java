package com.harness.events;

import java.time.LocalDateTime;

/**
 * 분석 완료 이벤트
 * analyzer-service → [Kafka: harness.analysis.completed] → (웹 알림, 대시보드 갱신 등)
 */
public record AnalysisCompletedEvent(
        String eventId,
        Long dataId,
        String analysisType,     // "anomaly" | "report" | "classification"
        String resultSummary,    // 결과 요약 (전체는 DB 조회)
        String severity,         // "LOW" | "MEDIUM" | "HIGH" | null
        LocalDateTime completedAt
) {}
