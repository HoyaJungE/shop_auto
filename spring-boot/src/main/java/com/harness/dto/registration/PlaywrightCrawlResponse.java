package com.harness.dto.registration;

import java.util.List;

public record PlaywrightCrawlResponse(
        boolean success,
        String taskId,
        String message
) {}
