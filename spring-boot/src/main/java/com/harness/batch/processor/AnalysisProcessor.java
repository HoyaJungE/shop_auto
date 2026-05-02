package com.harness.batch.processor;

import com.harness.agent.service.ClaudeAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * ② Spring Batch: ItemProcessor
 *
 * Reader에서 읽은 원본 데이터를 가공한다.
 * ① AI Agent와 연결되는 지점 — Claude에게 분석을 위임한다.
 *
 * [처리 흐름]
 * CollectedData(RAW) → AnalysisProcessor → AnalysisResult(PROCESSED)
 *
 * null 반환 시 해당 아이템은 Writer로 전달되지 않음 (필터링)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisProcessor implements ItemProcessor<Object[], AnalysisResult> {

    private final ClaudeAgentService claudeAgentService;

    @Override
    public AnalysisResult process(Object[] item) throws Exception {
        Long id = (Long) item[0];
        String payload = item[1] != null ? item[1].toString() : "";
        String dataType = item[2] != null ? item[2].toString() : "unknown";

        log.debug("[Batch] Processing id={}, dataType={}", id, dataType);

        try {
            String analysisResult = switch (dataType) {
                // 크롤링 데이터 → 콘텐츠 분류
                case "crawl" -> claudeAgentService.classifyContent(payload);
                // 로그 데이터 → 이상 탐지
                case "log"   -> claudeAgentService.analyzeAnomaly(payload);
                // 사용자 입력 → 요약
                default      -> claudeAgentService.generateReport(payload, "daily");
            };

            return new AnalysisResult(id, analysisResult, "PROCESSED");

        } catch (Exception e) {
            // 처리 실패 시 null 반환 → Writer 스킵, 별도 로그
            log.error("[Batch] AI 분석 실패 id={}: {}", id, e.getMessage());
            return new AnalysisResult(id, null, "ERROR");
        }
    }

}
