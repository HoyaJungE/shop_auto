package com.harness.controller.registration;

import com.harness.dto.registration.TaskCallbackPayload;
import com.harness.service.registration.ProductRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * playwright-service → Spring Boot 콜백 수신 컨트롤러
 *
 * 등록/크롤링 작업이 완료되면 playwright-service가 이 엔드포인트를 호출한다.
 * 인증 없이 내부 네트워크에서만 접근 가능해야 한다 (방화벽/Docker 네트워크로 제한).
 */
@Slf4j
@RestController
@RequestMapping("/internal/playwright")
@RequiredArgsConstructor
public class PlaywrightCallbackController {

    private final ProductRegistrationService registrationService;

    @PostMapping("/callback")
    public ResponseEntity<Void> callback(@RequestBody TaskCallbackPayload payload) {
        log.info("[Callback] 수신: taskId={}, status={}", payload.taskId(), payload.status());
        try {
            registrationService.handleCallback(payload);
        } catch (Exception e) {
            // 콜백 처리 실패는 로그만 남기고 200 응답 (playwright-service 재전송 방지)
            log.error("[Callback] 처리 실패: taskId={}, error={}", payload.taskId(), e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }
}
