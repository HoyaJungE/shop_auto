package com.harness.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Anthropic Claude API 응답 DTO
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClaudeResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("content")
    private List<ContentBlock> content;

    @JsonProperty("stop_reason")
    private String stopReason;

    @JsonProperty("usage")
    private Usage usage;

    /** 첫 번째 텍스트 블록 반환 (가장 일반적인 사용) */
    public String getFirstText() {
        if (content == null || content.isEmpty()) return "";
        return content.stream()
                .filter(c -> "text".equals(c.getType()))
                .findFirst()
                .map(ContentBlock::getText)
                .orElse("");
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentBlock {
        @JsonProperty("type")
        private String type;

        @JsonProperty("text")
        private String text;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {
        @JsonProperty("input_tokens")
        private int inputTokens;

        @JsonProperty("output_tokens")
        private int outputTokens;
    }
}
