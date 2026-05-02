package com.harness.analyzer.service;

import com.harness.agent.service.ClaudeAgentService;
import com.harness.events.DataCollectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * ③ analyzer-service: 핵심 분석 서비스
 *
 * DataCollectedEvent를 받아 ① AI Agent(Claude)에 분석을 위임하고
 * 결과를 DB에 저장한다.
 *
 * [① AI Agent 연동 구조]
 * AnalyzerConsumer (Kafka 수신)
 *   └── AnalyzerService.analyze()
 *         └── ClaudeAgentService.analyzeAnomaly() / classifyContent() / generateReport()
 *               └── Claude API (Anthropic)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyzerService {

    private final ClaudeAgentService claudeAgentService;
    private final JdbcTemplate jdbcTemplate;

    public AnalysisOutput analyze(DataCollectedEvent event) {
        // 1. 원본 데이터 payload 조회
        String payload = fetchPayload(event.dataId());

        if (payload == null || payload.isBlank()) {
            log.warn("[Analyzer] payload 없음 dataId={}", event.dataId());
            return new AnalysisOutput(event.dataId(), "classification", "{}", null);
        }

        // 2. dataType에 따라 AI 서브에이전트 분기
        String result = switch (event.dataType()) {
            case "log"        -> claudeAgentService.analyzeAnomaly(payload);
            case "crawl"      -> claudeAgentService.classifyContent(payload);
            case "user_input" -> claudeAgentService.generateReport(payload, "daily");
            default           -> claudeAgentService.query(
                    "데이터를 분석해 JSON으로 요약하세요.", payload);
        };

        // 3. severity 추출 (anomaly 분석 결과에만 존재)
        String severity = extractSeverity(result);

        // 4. 분석 결과 DB 저장
        saveResult(event.dataId(), event.dataType(), result);

        log.info("[Analyzer] 분석 완료 dataId={}, dataType={}, severity={}",
                event.dataId(), event.dataType(), severity);

        return new AnalysisOutput(event.dataId(), event.dataType(), result, severity);
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────────────────

    private String fetchPayload(Long dataId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT payload::text FROM collected_data WHERE id = ?",
                    String.class, dataId);
        } catch (Exception e) {
            log.error("[Analyzer] payload 조회 실패 dataId={}: {}", dataId, e.getMessage());
            return null;
        }
    }

    private void saveResult(Long dataId, String analysisType, String result) {
        jdbcTemplate.update("""
                INSERT INTO analysis_result (analysis_key, result, created_at)
                VALUES (?, ?::jsonb, NOW())
                ON CONFLICT (analysis_key, period_from, period_to) DO UPDATE
                  SET result = EXCLUDED.result, created_at = NOW()
                """,
                analysisType + "-" + dataId,
                result
        );
        jdbcTemplate.update(
                "UPDATE collected_data SET status = 'PROCESSED', processed_at = NOW() WHERE id = ?",
                dataId);
    }

    private String extractSeverity(String jsonResult) {
        if (jsonResult == null) return null;
        // 간단한 문자열 파싱 (실제로는 ObjectMapper 사용 권장)
        if (jsonResult.contains("\"severity\":\"HIGH\""))   return "HIGH";
        if (jsonResult.contains("\"severity\":\"MEDIUM\"")) return "MEDIUM";
        if (jsonResult.contains("\"severity\":\"LOW\""))    return "LOW";
        return null;
    }

    // ── 출력 모델 ──────────────────────────────────────────────────────────────
    public record AnalysisOutput(
            Long dataId,
            String analysisType,
            String summary,
            String severity
    ) {}
}
