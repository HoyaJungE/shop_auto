package com.harness.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Circuit Breaker 폴백 응답
 * 하위 서비스 장애 시 API Gateway가 이 응답을 반환한다.
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback")
    public ResponseEntity<Map<String, Object>> fallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "success", false,
                        "error", Map.of(
                                "code", "COMMON_007",
                                "message", "서비스가 일시적으로 사용 불가합니다. 잠시 후 다시 시도해주세요."
                        )
                ));
    }
}
