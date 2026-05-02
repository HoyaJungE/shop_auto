package com.harness.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

/**
 * 기본 RestClient 빈 설정
 *
 * PlaywrightTaskClient 등 내부 서비스 호출에 사용하는 범용 RestClient.
 * Claude API용 RestClient(claudeRestClient)와 분리한다.
 *
 * [빈 충돌 방지]
 * @Primary 로 기본 RestClient 타입 주입 시 이 빈이 선택되도록 한다.
 * ClaudeAgentService는 @Qualifier("claudeRestClient")로 명시적 지정.
 */
@Configuration
public class RestClientConfig {

    @Bean
    @Primary
    public RestClient restClient() {
        return RestClient.builder().build();
    }
}
