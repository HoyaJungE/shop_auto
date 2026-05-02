package com.harness.client;

import com.harness.domain.product.PlatformRegistration.Platform;
import com.harness.domain.product.Product;
import com.harness.domain.product.ProductImage;
import com.harness.domain.product.ProductOption;
import com.harness.dto.registration.PlaywrightRegisterRequest;
import com.harness.dto.registration.PlaywrightRegisterResponse;
import com.harness.dto.registration.PlaywrightCrawlRequest;
import com.harness.dto.registration.PlaywrightCrawlResponse;
import com.harness.dto.registration.TaskCallbackPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * playwright-service HTTP 클라이언트
 *
 * playwright-service는 별도 Node.js 프로세스이므로
 * RestClient로 HTTP 통신한다.
 * 작업은 비동기 큐로 처리되며, 완료 시 /internal/playwright/callback으로 콜백이 온다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaywrightTaskClient {

    private final RestClient restClient;

    @Value("${playwright.service.url:http://localhost:3100}")
    private String playwrightServiceUrl;

    // ── 상품 등록 요청 ─────────────────────────────────────────

    /**
     * playwright-service에 상품 등록 작업을 요청한다.
     * 비동기 방식으로 즉시 taskId를 반환하고,
     * 실제 완료는 콜백(/internal/playwright/callback)으로 수신한다.
     *
     * @return taskId
     */
    public String requestRegister(
            Product product,
            Platform platform,
            String categoryCode,
            PlatformCredential credential
    ) {
        String taskId = UUID.randomUUID().toString();

        PlaywrightRegisterRequest request = PlaywrightRegisterRequest.builder()
                .taskId(taskId)
                .platform(platform.name())
                .product(toProductPayload(product, categoryCode))
                .credentials(new PlaywrightRegisterRequest.Credentials(
                        credential.loginId(), credential.password()
                ))
                .build();

        try {
            PlaywrightRegisterResponse response = restClient
                    .post()
                    .uri(playwrightServiceUrl + "/tasks/register")
                    .body(request)
                    .retrieve()
                    .body(PlaywrightRegisterResponse.class);

            log.info("[Playwright] 등록 요청 성공: taskId={}, platform={}, product={}",
                    taskId, platform, product.getName());
            return taskId;
        } catch (RestClientException e) {
            log.error("[Playwright] 등록 요청 실패: taskId={}, error={}", taskId, e.getMessage());
            throw new PlaywrightServiceException("playwright-service 호출 실패: " + e.getMessage(), e);
        }
    }

    // ── Cafe24 크롤링 요청 ─────────────────────────────────────

    public String requestCrawl(String shopUrl, PlatformCredential credential, Integer limit) {
        String taskId = UUID.randomUUID().toString();

        PlaywrightCrawlRequest request = PlaywrightCrawlRequest.builder()
                .taskId(taskId)
                .credentials(new PlaywrightCrawlRequest.Credentials(
                        credential.loginId(), credential.password(), shopUrl
                ))
                .limit(limit)
                .build();

        try {
            restClient
                    .post()
                    .uri(playwrightServiceUrl + "/tasks/crawl")
                    .body(request)
                    .retrieve()
                    .body(PlaywrightRegisterResponse.class);

            log.info("[Playwright] 크롤링 요청 성공: taskId={}, shopUrl={}", taskId, shopUrl);
            return taskId;
        } catch (RestClientException e) {
            log.error("[Playwright] 크롤링 요청 실패: taskId={}, error={}", taskId, e.getMessage());
            throw new PlaywrightServiceException("playwright-service 크롤링 호출 실패: " + e.getMessage(), e);
        }
    }

    // ── 헬스체크 ──────────────────────────────────────────────

    public boolean isHealthy() {
        try {
            restClient
                    .get()
                    .uri(playwrightServiceUrl + "/tasks/health")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("[Playwright] 헬스체크 실패: {}", e.getMessage());
            return false;
        }
    }

    // ── 내부 변환 ─────────────────────────────────────────────

    private PlaywrightRegisterRequest.ProductPayload toProductPayload(
            Product product, String categoryCode
    ) {
        var images = product.getImages().stream()
                .map(img -> new PlaywrightRegisterRequest.ProductImage(
                        img.getImageUrl(),
                        img.getImageOrder(),
                        img.getImageType().name()
                ))
                .toList();

        var options = product.getOptions().stream()
                .map(opt -> new PlaywrightRegisterRequest.ProductOption(
                        opt.getOptionGroup(),
                        opt.getOptionValue(),
                        opt.getAdditionalPrice(),
                        opt.getStockQty()
                ))
                .toList();

        return PlaywrightRegisterRequest.ProductPayload.builder()
                .cafe24ProductId(product.getCafe24ProductId())
                .name(product.getName())
                .originalPrice(product.getOriginalPrice())
                .salePrice(product.getSalePrice())
                .description(product.getDescription() != null ? product.getDescription() : "")
                .images(images)
                .options(options)
                .categoryCode(categoryCode)
                .build();
    }

    // ── 자격증명 레코드 ──────────────────────────────────────

    public record PlatformCredential(String loginId, String password) {}

    // ── 예외 ─────────────────────────────────────────────────

    public static class PlaywrightServiceException extends RuntimeException {
        public PlaywrightServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
