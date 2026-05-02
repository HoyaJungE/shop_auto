package com.harness.client;

import com.harness.domain.product.PlatformRegistration.Platform;
import com.harness.domain.product.Product;
import com.harness.domain.product.ProductImage;
import com.harness.domain.product.ProductOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.*;

/**
 * PlaywrightTaskClient 단위 테스트
 *
 * RestClient 체인(body/retrieve)의 통합 동작은 WireMock 기반 통합 테스트로 검증한다.
 * 이 파일에서는 도메인 계층(PlatformCredential, PlaywrightServiceException)과
 * 예외 래핑 등 단위 수준 검증에 집중한다.
 */
@DisplayName("PlaywrightTaskClient 테스트")
class PlaywrightTaskClientTest {

    private Product testProduct;

    @BeforeEach
    void setUp() {
        testProduct = Product.create("P001", "테스트 이불", 79000, 59000, "침구", "<p>설명</p>");
        testProduct.addImage(ProductImage.of("https://img.com/1.jpg", 0, ProductImage.ImageType.REPRESENTATIVE));
        testProduct.addOption(ProductOption.of("색상", "아이보리", 50, 0));
    }

    // ── PlatformCredential 레코드 검증 ────────────────────────────────────────

    @Test
    @DisplayName("PlatformCredential 레코드가 loginId/password를 올바르게 저장한다")
    void platformCredential_holdsValues() {
        var cred = new PlaywrightTaskClient.PlatformCredential("user@test.com", "secret123");
        assertThat(cred.loginId()).isEqualTo("user@test.com");
        assertThat(cred.password()).isEqualTo("secret123");
    }

    @Test
    @DisplayName("PlatformCredential은 같은 값이면 동등하다 (record equals)")
    void platformCredential_equality() {
        var cred1 = new PlaywrightTaskClient.PlatformCredential("a@b.com", "pw");
        var cred2 = new PlaywrightTaskClient.PlatformCredential("a@b.com", "pw");
        assertThat(cred1).isEqualTo(cred2);
    }

    // ── PlaywrightServiceException 검증 ──────────────────────────────────────

    @Test
    @DisplayName("PlaywrightServiceException은 메시지와 원인을 전달한다")
    void playwrightServiceException_wrapsOriginalCause() {
        var cause = new RestClientException("timeout");
        var ex = new PlaywrightTaskClient.PlaywrightServiceException(
                "playwright-service 호출 실패: timeout", cause
        );

        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).contains("playwright-service 호출 실패");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("PlaywrightServiceException은 RuntimeException을 상속한다")
    void playwrightServiceException_isRuntimeException() {
        var ex = new PlaywrightTaskClient.PlaywrightServiceException("test", new RuntimeException());
        assertThat(ex).isInstanceOf(RuntimeException.class);
        // 비검사 예외이므로 try-catch 없이 전파 가능
    }
}
