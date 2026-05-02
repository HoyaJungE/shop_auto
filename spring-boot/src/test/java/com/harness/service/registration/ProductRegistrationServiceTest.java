package com.harness.service.registration;

import com.harness.agent.service.ClaudeAgentService;
import com.harness.client.PlaywrightTaskClient;
import com.harness.client.PlaywrightTaskClient.PlatformCredential;
import com.harness.domain.product.*;
import com.harness.domain.product.PlatformRegistration.Platform;
import com.harness.dto.registration.TaskCallbackPayload;
import com.harness.exception.BusinessException;
import com.harness.repository.product.CategoryMappingRepository;
import com.harness.repository.product.PlatformRegistrationRepository;
import com.harness.repository.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductRegistrationService 테스트")
class ProductRegistrationServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private PlatformRegistrationRepository registrationRepository;
    @Mock private CategoryMappingRepository categoryMappingRepository;
    @Mock private PlaywrightTaskClient playwrightClient;
    @Mock private ClaudeAgentService claudeAgentService;

    @InjectMocks private ProductRegistrationService service;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "coupangLoginId", "coupang@test.com");
        ReflectionTestUtils.setField(service, "coupangPassword", "pass");
        ReflectionTestUtils.setField(service, "naverLoginId",   "naver@test.com");
        ReflectionTestUtils.setField(service, "naverPassword",  "pass");
        ReflectionTestUtils.setField(service, "ohouseLoginId",  "ohouse@test.com");
        ReflectionTestUtils.setField(service, "ohousePassword", "pass");

        testProduct = Product.create("P001", "구스다운 이불", 79000, 59000, "침구", "<p>설명</p>");
        testProduct.addImage(ProductImage.of("https://img.com/1.jpg", 0, ProductImage.ImageType.REPRESENTATIVE));
    }

    // ── registerToPlatform ────────────────────────────────────

    @Test
    @DisplayName("쿠팡 등록 요청 성공 시 taskId를 반환한다")
    void registerToPlatform_success_returnsTaskId() {
        // given
        CategoryMapping mapping = CategoryMapping.create(
                "침구", Platform.COUPANG, "10001234", "침구/이불"
        );
        mapping.confirm();

        when(productRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(testProduct));
        when(categoryMappingRepository.findByCafe24CategoryAndPlatform("침구", Platform.COUPANG))
                .thenReturn(Optional.of(mapping));
        when(registrationRepository.findByProductIdAndPlatform(any(), eq(Platform.COUPANG)))
                .thenReturn(Optional.empty());
        when(registrationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(playwrightClient.requestRegister(any(), eq(Platform.COUPANG), eq("10001234"), any()))
                .thenReturn("task-uuid-001");

        // when
        String taskId = service.registerToPlatform(1L, Platform.COUPANG);

        // then
        assertThat(taskId).isEqualTo("task-uuid-001");
        verify(playwrightClient).requestRegister(eq(testProduct), eq(Platform.COUPANG),
                eq("10001234"), any(PlatformCredential.class));
    }

    @Test
    @DisplayName("확인되지 않은 카테고리 매핑이면 BusinessException을 던진다")
    void registerToPlatform_unconfirmedMapping_throwsException() {
        // given
        CategoryMapping unconfirmedMapping = CategoryMapping.create(
                "침구", Platform.COUPANG, "10001234", "침구/이불"
        );
        // confirm() 호출하지 않음

        when(productRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(testProduct));
        when(categoryMappingRepository.findByCafe24CategoryAndPlatform("침구", Platform.COUPANG))
                .thenReturn(Optional.of(unconfirmedMapping));

        // when & then
        assertThatThrownBy(() -> service.registerToPlatform(1L, Platform.COUPANG))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("이미 RUNNING 상태면 BusinessException을 던진다")
    void registerToPlatform_alreadyRunning_throwsException() {
        // given
        CategoryMapping mapping = CategoryMapping.create("침구", Platform.COUPANG, "10001234", "침구");
        mapping.confirm();

        PlatformRegistration runningReg = PlatformRegistration.create(testProduct, Platform.COUPANG);
        runningReg.start("existing-task");

        when(productRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(testProduct));
        when(categoryMappingRepository.findByCafe24CategoryAndPlatform("침구", Platform.COUPANG))
                .thenReturn(Optional.of(mapping));
        when(registrationRepository.findByProductIdAndPlatform(any(), eq(Platform.COUPANG)))
                .thenReturn(Optional.of(runningReg));

        // when & then
        assertThatThrownBy(() -> service.registerToPlatform(1L, Platform.COUPANG))
                .isInstanceOf(BusinessException.class);
    }

    // ── handleCallback (등록 결과) ────────────────────────────

    @Test
    @DisplayName("SUCCESS 콜백 → PlatformRegistration을 SUCCESS로 업데이트한다")
    void handleCallback_success_updatesRegistration() {
        // given
        PlatformRegistration reg = PlatformRegistration.create(testProduct, Platform.COUPANG);
        reg.start("task-001");

        TaskCallbackPayload payload = new TaskCallbackPayload(
                "task-001", "SUCCESS", "CP-99999",
                null, null, "2026-05-02T10:00:00Z", null
        );

        when(registrationRepository.findByTaskId("task-001")).thenReturn(Optional.of(reg));
        when(registrationRepository.findByProductId(any())).thenReturn(List.of(reg));

        // when
        service.handleCallback(payload);

        // then
        assertThat(reg.getStatus()).isEqualTo(PlatformRegistration.Status.SUCCESS);
        assertThat(reg.getPlatformProductId()).isEqualTo("CP-99999");
    }

    @Test
    @DisplayName("FAILED 콜백 → PlatformRegistration을 FAILED로 업데이트하고 상품을 ERROR로 표시한다")
    void handleCallback_failed_updatesRegistrationAndProduct() {
        // given
        PlatformRegistration reg = PlatformRegistration.create(testProduct, Platform.COUPANG);
        reg.start("task-002");

        TaskCallbackPayload payload = new TaskCallbackPayload(
                "task-002", "FAILED", null,
                "로그인 실패", "screenshots/err.png", "2026-05-02T10:00:00Z", null
        );

        when(registrationRepository.findByTaskId("task-002")).thenReturn(Optional.of(reg));

        // when
        service.handleCallback(payload);

        // then
        assertThat(reg.getStatus()).isEqualTo(PlatformRegistration.Status.FAILED);
        assertThat(reg.getErrorMessage()).isEqualTo("로그인 실패");
        assertThat(testProduct.getStatus()).isEqualTo(Product.Status.ERROR);
    }

    @Test
    @DisplayName("모든 플랫폼 SUCCESS 시 상품 상태가 DONE으로 변경된다")
    void handleCallback_allPlatformsSuccess_marksProductDone() {
        // given
        PlatformRegistration coupangReg = PlatformRegistration.create(testProduct, Platform.COUPANG);
        coupangReg.start("task-c");
        PlatformRegistration naverReg = PlatformRegistration.create(testProduct, Platform.NAVER);
        naverReg.start("task-n");
        naverReg.succeed("NV-001");  // 네이버는 이미 성공

        TaskCallbackPayload payload = new TaskCallbackPayload(
                "task-c", "SUCCESS", "CP-001",
                null, null, "2026-05-02T10:00:00Z", null
        );

        when(registrationRepository.findByTaskId("task-c")).thenReturn(Optional.of(coupangReg));
        when(registrationRepository.findByProductId(any()))
                .thenReturn(List.of(coupangReg, naverReg));

        // when
        service.handleCallback(payload);

        // then
        assertThat(coupangReg.getStatus()).isEqualTo(PlatformRegistration.Status.SUCCESS);
        assertThat(testProduct.getStatus()).isEqualTo(Product.Status.DONE);
    }

    // ── handleCallback (크롤링 결과) ──────────────────────────

    @Test
    @DisplayName("크롤링 콜백으로 받은 상품을 DB에 저장한다")
    void handleCallback_crawlResult_savesProducts() {
        // given
        var crawledProducts = List.of(
                new TaskCallbackPayload.CrawledProductDto(
                        "C001", "이불 세트", 79000, 59000, "침구", "<p>설명</p>",
                        List.of(new TaskCallbackPayload.ImageDto("https://img.com/1.jpg", 0, "REPRESENTATIVE")),
                        List.of(new TaskCallbackPayload.OptionDto("색상", "아이보리", 0, 50))
                )
        );
        TaskCallbackPayload payload = new TaskCallbackPayload(
                "crawl-001", "SUCCESS", null, null, null, null, crawledProducts
        );

        when(productRepository.existsByCafe24ProductId("C001")).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(claudeAgentService.query(any(), any())).thenReturn("{\"categoryId\": \"10001\", \"categoryName\": \"이불\"}");
        when(categoryMappingRepository.findByCafe24CategoryAndPlatform(any(), any()))
                .thenReturn(Optional.empty());
        when(categoryMappingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        service.handleCallback(payload);

        // then
        verify(productRepository).save(argThat(p ->
                "C001".equals(p.getCafe24ProductId()) && "이불 세트".equals(p.getName())
        ));
    }

    @Test
    @DisplayName("이미 존재하는 Cafe24 상품 ID는 저장을 스킵한다")
    void handleCallback_existingProduct_skipsInsert() {
        // given
        var crawledProducts = List.of(
                new TaskCallbackPayload.CrawledProductDto(
                        "EXISTING", "이미 있는 상품", 50000, 40000, "침구", "",
                        List.of(), List.of()
                )
        );
        TaskCallbackPayload payload = new TaskCallbackPayload(
                "crawl-002", "SUCCESS", null, null, null, null, crawledProducts
        );

        when(productRepository.existsByCafe24ProductId("EXISTING")).thenReturn(true);

        // when
        service.handleCallback(payload);

        // then
        verify(productRepository, never()).save(any());
    }
}
