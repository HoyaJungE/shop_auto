package com.harness.controller.registration;

import com.harness.domain.product.PlatformRegistration.Platform;
import com.harness.domain.product.Product;
import com.harness.dto.ApiResponse;
import com.harness.service.registration.ProductRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Product Registration", description = "상품 자동등록 API")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductRegistrationController {

    private final ProductRegistrationService registrationService;

    // ── 상품 목록 ──────────────────────────────────────────────

    @Operation(summary = "상품 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductSummaryDto>>> getProducts(
            @RequestParam(required = false) Product.Status status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ProductSummaryDto> result = registrationService
                .getProducts(status, pageable)
                .map(ProductSummaryDto::from);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "상품 상세 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDetailDto>> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                ProductDetailDto.from(registrationService.getProduct(id))
        ));
    }

    // ── 등록 요청 ──────────────────────────────────────────────

    @Operation(summary = "특정 플랫폼에 상품 등록")
    @PostMapping("/{id}/register/{platform}")
    public ResponseEntity<ApiResponse<Map<String, String>>> register(
            @PathVariable Long id,
            @PathVariable Platform platform
    ) {
        String taskId = registrationService.registerToPlatform(id, platform);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("taskId", taskId)));
    }

    @Operation(summary = "모든 플랫폼에 일괄 등록")
    @PostMapping("/{id}/register/all")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> registerAll(@PathVariable Long id) {
        List<String> taskIds = registrationService.registerToAllPlatforms(id);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("taskIds", taskIds)));
    }

    // ── Cafe24 크롤링 ─────────────────────────────────────────

    @Operation(summary = "Cafe24 상품 크롤링 요청")
    @PostMapping("/crawl")
    public ResponseEntity<ApiResponse<Map<String, String>>> crawl(
            @RequestParam String shopUrl,
            @RequestParam(required = false) Integer limit
    ) {
        String taskId = registrationService.requestCrawl(shopUrl, limit);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("taskId", taskId)));
    }

    // ── 카테고리 매핑 ─────────────────────────────────────────

    @Operation(summary = "미확인 카테고리 매핑 목록")
    @GetMapping("/category-mappings/unconfirmed")
    public ResponseEntity<ApiResponse<List<?>>> getUnconfirmedMappings() {
        return ResponseEntity.ok(ApiResponse.ok(registrationService.getUnconfirmedMappings()));
    }

    @Operation(summary = "카테고리 매핑 확인")
    @PatchMapping("/category-mappings/{mappingId}/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmMapping(@PathVariable Long mappingId) {
        registrationService.confirmMapping(mappingId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── DTO ──────────────────────────────────────────────────

    public record ProductSummaryDto(
            Long id,
            String cafe24ProductId,
            String name,
            Integer salePrice,
            String categoryName,
            Product.Status status
    ) {
        static ProductSummaryDto from(Product p) {
            return new ProductSummaryDto(
                    p.getId(), p.getCafe24ProductId(), p.getName(),
                    p.getSalePrice(), p.getCategoryName(), p.getStatus()
            );
        }
    }

    public record ProductDetailDto(
            Long id,
            String cafe24ProductId,
            String name,
            Integer originalPrice,
            Integer salePrice,
            String categoryName,
            Product.Status status,
            List<ImageDto> images,
            List<OptionDto> options,
            List<RegistrationDto> registrations
    ) {
        record ImageDto(String url, Integer order, String type) {}
        record OptionDto(String group, String value, Integer stockQty) {}
        record RegistrationDto(String platform, String status, String platformProductId, String errorMessage) {}

        static ProductDetailDto from(Product p) {
            return new ProductDetailDto(
                    p.getId(), p.getCafe24ProductId(), p.getName(),
                    p.getOriginalPrice(), p.getSalePrice(), p.getCategoryName(), p.getStatus(),
                    p.getImages().stream()
                            .map(i -> new ImageDto(i.getImageUrl(), i.getImageOrder(), i.getImageType().name()))
                            .toList(),
                    p.getOptions().stream()
                            .map(o -> new OptionDto(o.getOptionGroup(), o.getOptionValue(), o.getStockQty()))
                            .toList(),
                    p.getRegistrations().stream()
                            .map(r -> new RegistrationDto(
                                    r.getPlatform().name(), r.getStatus().name(),
                                    r.getPlatformProductId(), r.getErrorMessage()))
                            .toList()
            );
        }
    }
}
