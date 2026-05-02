package com.harness.agent.service;

import com.harness.agent.dto.ClaudeRequest;
import com.harness.agent.dto.ClaudeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * ① AI Agent: Claude API 호출 서비스
 *
 * [서브에이전트 패턴]
 * - 각 메서드가 하나의 "에이전트 태스크"를 담당
 * - 복잡한 작업은 여러 메서드를 조합해 Orchestrator → SubAgent 구조로 확장
 *
 * [확장 패턴]
 * Orchestrator (ClaudeAgentService)
 *   ├── analyzeData()       ← 데이터 이상 탐지 서브에이전트
 *   ├── summarizeReport()   ← 보고서 요약 서브에이전트
 *   └── classifyContent()   ← 콘텐츠 분류 서브에이전트
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaudeAgentService {

    @Qualifier("claudeRestClient")
    private final RestClient claudeRestClient;

    @Value("${claude.model:claude-opus-4-5}")
    private String model;

    @Value("${claude.max-tokens:2048}")
    private int maxTokens;

    private static final String MESSAGES_PATH = "/v1/messages";

    // ── 서브에이전트 태스크들 ──────────────────────────────────────────────────

    /**
     * 서브에이전트 ①: 수집 데이터 이상 탐지
     * @param rawData JSON 또는 텍스트 형태의 수집 데이터
     * @return 이상 패턴 분석 결과
     */
    public String analyzeAnomaly(String rawData) {
        String systemPrompt = """
                당신은 데이터 이상 탐지 전문가입니다.
                주어진 데이터를 분석해 이상 패턴, 이상값, 비정상적인 추세를 찾아내세요.
                응답은 반드시 JSON 형식으로: {"anomalies": [...], "severity": "LOW|MEDIUM|HIGH", "summary": "..."}
                """;

        return call(systemPrompt, "다음 데이터를 분석해주세요:\n" + rawData);
    }

    /**
     * 서브에이전트 ②: 수집 데이터 요약 보고서 생성
     * @param data 분석할 원본 데이터
     * @param reportType 보고서 유형 (daily, weekly, monthly)
     * @return 마크다운 형식의 보고서
     */
    public String generateReport(String data, String reportType) {
        String systemPrompt = String.format("""
                당신은 데이터 분석 보고서 작성 전문가입니다.
                %s 보고서를 작성하세요.
                형식: 마크다운, 핵심 지표 → 트렌드 → 액션 아이템 순서
                """, reportType);

        return call(systemPrompt, data);
    }

    /**
     * 서브에이전트 ③: 크롤링 콘텐츠 분류 및 태깅
     * @param content 크롤링된 텍스트 콘텐츠
     * @return 분류 결과 JSON
     */
    public String classifyContent(String content) {
        String systemPrompt = """
                당신은 콘텐츠 분류 전문가입니다.
                주어진 텍스트를 분석해 카테고리, 키워드, 감성을 추출하세요.
                응답 JSON: {"category": "...", "keywords": [...], "sentiment": "positive|neutral|negative", "confidence": 0.0~1.0}
                """;

        return call(systemPrompt, content);
    }

    /**
     * 서브에이전트 ④: 자유 형식 질의 (Orchestrator에서 직접 호출)
     */
    public String query(String systemPrompt, String userMessage) {
        return call(systemPrompt, userMessage);
    }

    // ── 내부 공통 호출 ────────────────────────────────────────────────────────

    private String call(String systemPrompt, String userContent) {
        ClaudeRequest request = ClaudeRequest.userMessage(model, maxTokens, systemPrompt, userContent);

        log.debug("[ClaudeAgent] 호출 model={}, inputLength={}", model, userContent.length());

        ClaudeResponse response = claudeRestClient.post()
                .uri(MESSAGES_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ClaudeResponse.class);

        if (response == null) {
            throw new IllegalStateException("Claude API 응답이 null입니다.");
        }

        log.debug("[ClaudeAgent] 완료 inputTokens={}, outputTokens={}",
                response.getUsage() != null ? response.getUsage().getInputTokens() : 0,
                response.getUsage() != null ? response.getUsage().getOutputTokens() : 0);

        return response.getFirstText();
    }
}
