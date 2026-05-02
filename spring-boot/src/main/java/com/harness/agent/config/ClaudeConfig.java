package com.harness.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Claude API RestClient 설정
 *
 * Spring 6.1+ RestClient 사용 (동기, WebFlux 불필요)
 * application.yml에 claude.api-key 설정 필요
 */
@Configuration
public class ClaudeConfig {

    private static final String CLAUDE_API_URL = "https://api.anthropic.com";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Value("${claude.api-key}")
    private String apiKey;

    @Bean("claudeRestClient")
    public RestClient claudeRestClient() {
        return RestClient.builder()
                .baseUrl(CLAUDE_API_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .build();
    }
}
