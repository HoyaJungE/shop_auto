package com.harness.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Anthropic Claude API 요청 DTO
 * API 문서: https://docs.anthropic.com/en/api/messages
 */
@Getter
@Builder
public class ClaudeRequest {

    @JsonProperty("model")
    private String model;               // 예: "claude-opus-4-5"

    @JsonProperty("max_tokens")
    private int maxTokens;

    @JsonProperty("system")
    private String system;              // 시스템 프롬프트

    @JsonProperty("messages")
    private List<Message> messages;

    @Getter
    @Builder
    public static class Message {
        @JsonProperty("role")
        private String role;            // "user" | "assistant"

        @JsonProperty("content")
        private String content;
    }

    /** 단순 사용자 메시지 1개짜리 요청 생성 헬퍼 */
    public static ClaudeRequest userMessage(String model, int maxTokens,
                                            String systemPrompt, String userContent) {
        return ClaudeRequest.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(systemPrompt)
                .messages(List.of(Message.builder()
                        .role("user")
                        .content(userContent)
                        .build()))
                .build();
    }
}
