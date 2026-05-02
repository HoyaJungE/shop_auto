package com.harness.domain.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PlatformRegistration 도메인 테스트")
class PlatformRegistrationTest {

    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.create("P001", "이불", 50000, 40000, "침구", "설명");
    }

    @Test
    @DisplayName("create()로 PENDING 상태의 등록 이력을 생성한다")
    void create_defaultPending() {
        PlatformRegistration reg = PlatformRegistration.create(product, PlatformRegistration.Platform.COUPANG);

        assertThat(reg.getPlatform()).isEqualTo(PlatformRegistration.Platform.COUPANG);
        assertThat(reg.getStatus()).isEqualTo(PlatformRegistration.Status.PENDING);
        assertThat(reg.getTaskId()).isNull();
        assertThat(reg.getPlatformProductId()).isNull();
    }

    @Test
    @DisplayName("start()는 RUNNING 상태로 전이하고 taskId를 저장한다")
    void start_setsRunningAndTaskId() {
        PlatformRegistration reg = PlatformRegistration.create(product, PlatformRegistration.Platform.NAVER);

        reg.start("task-uuid-001");

        assertThat(reg.getStatus()).isEqualTo(PlatformRegistration.Status.RUNNING);
        assertThat(reg.getTaskId()).isEqualTo("task-uuid-001");
    }

    @Test
    @DisplayName("succeed()는 SUCCESS 상태로 전이하고 platformProductId를 저장한다")
    void succeed_setsSuccessAndProductId() {
        PlatformRegistration reg = PlatformRegistration.create(product, PlatformRegistration.Platform.COUPANG);
        reg.start("task-001");

        reg.succeed("CP-12345");

        assertThat(reg.getStatus()).isEqualTo(PlatformRegistration.Status.SUCCESS);
        assertThat(reg.getPlatformProductId()).isEqualTo("CP-12345");
        assertThat(reg.getRegisteredAt()).isNotNull();
    }

    @Test
    @DisplayName("fail()은 FAILED 상태로 전이하고 오류 정보를 저장한다")
    void fail_setsFailedWithError() {
        PlatformRegistration reg = PlatformRegistration.create(product, PlatformRegistration.Platform.OHOUSE);
        reg.start("task-002");

        reg.fail("로그인 실패", "screenshots/error.png");

        assertThat(reg.getStatus()).isEqualTo(PlatformRegistration.Status.FAILED);
        assertThat(reg.getErrorMessage()).isEqualTo("로그인 실패");
        assertThat(reg.getScreenshotPath()).isEqualTo("screenshots/error.png");
    }

    @Test
    @DisplayName("resetForRetry()는 PENDING 상태로 초기화한다")
    void resetForRetry_clearsPreviousState() {
        PlatformRegistration reg = PlatformRegistration.create(product, PlatformRegistration.Platform.COUPANG);
        reg.start("task-003");
        reg.fail("오류", "screenshots/err.png");

        reg.resetForRetry();

        assertThat(reg.getStatus()).isEqualTo(PlatformRegistration.Status.PENDING);
        assertThat(reg.getTaskId()).isNull();
        assertThat(reg.getErrorMessage()).isNull();
        assertThat(reg.getScreenshotPath()).isNull();
    }
}
