package com.harness.dto.registration;

import lombok.Builder;

@Builder
public record PlaywrightCrawlRequest(
        String taskId,
        Credentials credentials,
        Integer limit
) {
    @Builder
    public record Credentials(String loginId, String password, String shopUrl) {}
}
