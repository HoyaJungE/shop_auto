package com.harness.dto.registration;

public record PlaywrightRegisterResponse(
        boolean success,
        String taskId,
        Integer position,
        String message
) {}
