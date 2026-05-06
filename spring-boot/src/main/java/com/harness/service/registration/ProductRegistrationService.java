package com.harness.service.registration;

import com.harness.agent.service.ClaudeAgentService;
import com.harness.client.PlaywrightTaskClient;
import com.harness.client.PlaywrightTaskClient.PlatformCredential;
import com.harness.domain.product.*;
import com.harness.domain.product.PlatformRegistration.Platform;
import com.harness.dto.registration.TaskCallbackPayload;
import com.harness.exception.BusinessException;
import com.harness.exception.ErrorCode;
import com.harness.repository.product.CategoryMappingRepository;
import com.harness.repository.product.PlatformRegistrationRepository;
import com.harness.repository.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductRegistrationService {

    private final ProductRepository productRepository;
    private final PlatformRegistrationRepository registrationRepository;
    private final CategoryMappingRepository categoryMappingRepository;
    private final PlaywrightTaskClient playwrightClient;
    private final ClaudeAgentService claudeAgentService;

    // 환경변수 또는 DB에서 관리 (실제 운영에서는 암호화 필수)
    @Value("${platform.coupang.login-id:}") private String coupangLoginId;
    @Value("${platform.coupang.password:}") private String coupangPassword;
    @Value("${platform.naver.login-id:}")   private String naverLoginId;
    @Value("${platform.naver.password:}")   private String naverPassword;
    @Value("${platform.ohouse.login-id:}")  private String ohouseLoginId;
    @Value("${platform.ohouse.password:}")  private String ohousePassword;

    // ── 상품 목록 조회 ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Product> getProducts(Product.Status status, Pageable pageable) {
        if (status != null) return productRepository.findByStatus(status, pageable);
        return productRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Product getProduct(Long id) {
        Product product = productRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        // registrations는 FETCH 대상 아님 (MultipleBagFetchException 방지).
        // OSIV 비활성화 상태이므로 트랜잭션 내에서 강제 초기화.
        product.getRegistrations().size();
        return product;
    }

    // ── 플랫폼 등록 요청 ──────────────────────────────────────

    /**
     * 특정 상품을 지정 플랫폼에 등록 요청한다.
     * playwright-service에 비동기로 작업을 전달하고 taskId를 반환한다.
     */
    @Transactional
    public String registerToPlatform(Long productId, Platform platform) {
        Product product = productRepository.findByIdWithDetails(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        // 카테고리 매핑 확인
        String categoryCode = getCategoryCode(product.getCategoryName(), platform);

        // 등록 이력 생성 또는 재시도
        PlatformRegistration reg = registrationRepository
                .findByProductIdAndPlatform(productId, platform)
                .orElseGet(() -> {
                    PlatformRegistration newReg = PlatformRegistration.create(product, platform);
                    return registrationRepository.save(newReg);
                });

        if (reg.getStatus() == PlatformRegistration.Status.RUNNING) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR); // 이미 진행 중
        }

        // playwright-service 호출
        PlatformCredential credential = getCredential(platform);
        String taskId = playwrightClient.requestRegister(product, platform, categoryCode, credential);

        reg.start(taskId);
        product.markPublishing();

        log.info("[Registration] 등록 요청: productId={}, platform={}, taskId={}", productId, platform, taskId);
        return taskId;
    }

    /**
     * 여러 플랫폼에 동시 등록 요청 (순차적으로 큐에 추가됨)
     */
    @Transactional
    public List<String> registerToAllPlatforms(Long productId) {
        return List.of(Platform.values()).stream()
                .map(platform -> registerToPlatform(productId, platform))
                .toList();
    }

    /**
     * 실패한 등록 재시도: FAILED 상태를 초기화 후 재등록
     */
    @Transactional
    public String retryRegister(Long productId, Platform platform) {
        PlatformRegistration reg = registrationRepository
                .findByProductIdAndPlatform(productId, platform)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (reg.getStatus() != PlatformRegistration.Status.FAILED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        reg.resetForRetry(); // FAILED → PENDING 초기화
        return registerToPlatform(productId, platform);
    }

    // ── 콜백 처리 ─────────────────────────────────────────────

    /**
     * playwright-service로부터 작업 완료 콜백을 수신한다.
     * 등록 성공/실패 상태를 DB에 반영한다.
     */
    @Transactional
    public void handleCallback(TaskCallbackPayload payload) {
        // 크롤링 결과 처리
        if (payload.products() != null && !payload.products().isEmpty()) {
            handleCrawlCallback(payload);
            return;
        }
        // 등록 결과 처리
        handleRegisterCallback(payload);
    }

    private void handleRegisterCallback(TaskCallbackPayload payload) {
        PlatformRegistration reg = registrationRepository
                .findByTaskId(payload.taskId())
                .orElseGet(() -> {
                    log.warn("[Callback] taskId를 찾을 수 없음: {}", payload.taskId());
                    return null;
                });
        if (reg == null) return;

        if ("SUCCESS".equals(payload.status())) {
            reg.succeed(payload.platformProductId());
            log.info("[Callback] 등록 성공: taskId={}, platformProductId={}",
                    payload.taskId(), payload.platformProductId());

            // 모든 플랫폼 성공 시 상품 상태 DONE으로 변경
            checkAndMarkDone(reg.getProduct());
        } else {
            reg.fail(payload.errorMessage(), payload.screenshotPath());
            reg.getProduct().markError();
            log.warn("[Callback] 등록 실패: taskId={}, error={}", payload.taskId(), payload.errorMessage());
        }
    }

    private void handleCrawlCallback(TaskCallbackPayload payload) {
        log.info("[Callback] 크롤링 완료: taskId={}, 상품 수={}", payload.taskId(), payload.products().size());

        for (TaskCallbackPayload.CrawledProductDto dto : payload.products()) {
            if (productRepository.existsByCafe24ProductId(dto.cafe24ProductId())) {
                log.debug("[Crawl] 이미 존재하는 상품 스킵: {}", dto.cafe24ProductId());
                continue;
            }

            Product product = Product.create(
                    dto.cafe24ProductId(), dto.name(),
                    dto.originalPrice(), dto.salePrice(),
                    dto.categoryName(), dto.description()
            );

            if (dto.images() != null) {
                dto.images().forEach(img -> product.addImage(
                        ProductImage.of(img.url(), img.order(),
                                ProductImage.ImageType.valueOf(img.type()))
                ));
            }
            if (dto.options() != null) {
                dto.options().forEach(opt -> product.addOption(
                        ProductOption.of(opt.groupName(), opt.value(),
                                opt.stockQty(), opt.additionalPrice())
                ));
            }

            productRepository.save(product);
            log.info("[Crawl] 상품 저장: {}", dto.name());
        }

        // AI로 카테고리 매핑 자동 제안
        suggestCategoryMappings(payload.products());
    }

    // ── Cafe24 크롤링 요청 ────────────────────────────────────

    @Transactional
    public String requestCrawl(String shopUrl, Integer limit) {
        PlatformCredential credential = new PlatformCredential(
                System.getenv("CAFE24_LOGIN_ID"),
                System.getenv("CAFE24_PASSWORD")
        );
        return playwrightClient.requestCrawl(shopUrl, credential, limit);
    }

    // ── 카테고리 매핑 관리 ────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CategoryMapping> getUnconfirmedMappings() {
        return categoryMappingRepository.findByConfirmedFalse();
    }

    @Transactional
    public void confirmMapping(Long mappingId) {
        CategoryMapping mapping = categoryMappingRepository.findById(mappingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        mapping.confirm();
    }

    // ── 내부 유틸 ─────────────────────────────────────────────

    private String getCategoryCode(String cafe24Category, Platform platform) {
        return categoryMappingRepository
                .findByCafe24CategoryAndPlatform(cafe24Category, platform)
                .filter(CategoryMapping::isConfirmed)
                .map(CategoryMapping::getPlatformCategoryId)
                .orElseThrow(() -> {
                    log.warn("[Category] 확인된 카테고리 매핑 없음: cafe24={}, platform={}", cafe24Category, platform);
                    return new BusinessException(ErrorCode.VALIDATION_ERROR);
                });
    }

    private PlatformCredential getCredential(Platform platform) {
        return switch (platform) {
            case COUPANG -> new PlatformCredential(coupangLoginId, coupangPassword);
            case NAVER   -> new PlatformCredential(naverLoginId,   naverPassword);
            case OHOUSE  -> new PlatformCredential(ohouseLoginId,  ohousePassword);
        };
    }

    private void checkAndMarkDone(Product product) {
        List<PlatformRegistration> regs = registrationRepository.findByProductId(product.getId());
        boolean allSuccess = regs.stream()
                .allMatch(r -> r.getStatus() == PlatformRegistration.Status.SUCCESS);
        if (allSuccess && !regs.isEmpty()) product.markDone();
    }

    private void suggestCategoryMappings(List<TaskCallbackPayload.CrawledProductDto> products) {
        try {
            List<String> categories = products.stream()
                    .map(TaskCallbackPayload.CrawledProductDto::categoryName)
                    .filter(c -> c != null && !c.isBlank())
                    .distinct()
                    .toList();

            for (String cafe24Category : categories) {
                for (Platform platform : Platform.values()) {
                    if (categoryMappingRepository.findByCafe24CategoryAndPlatform(cafe24Category, platform).isPresent()) continue;

                    String prompt = String.format(
                            "카페24 카테고리 '%s'에 해당하는 %s 플랫폼의 카테고리 코드와 이름을 JSON으로 답해줘. " +
                            "형식: {\"categoryId\": \"코드\", \"categoryName\": \"이름\"}",
                            cafe24Category, platform.name()
                    );
                    String suggestion = claudeAgentService.query("상품 카테고리 매핑 전문가", prompt);
                    log.info("[AI] 카테고리 매핑 제안 — {}/{}: {}", cafe24Category, platform, suggestion);

                    // 간단 파싱 (실제로는 ObjectMapper 사용 권장)
                    categoryMappingRepository.save(
                            CategoryMapping.create(cafe24Category, platform, "PENDING_CONFIRM", suggestion)
                    );
                }
            }
        } catch (Exception e) {
            log.warn("[AI] 카테고리 매핑 제안 실패 (무시): {}", e.getMessage());
        }
    }
}
