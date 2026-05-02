package com.harness.dto.registration;

/**
 * playwright-service → Spring Boot 콜백 페이로드
 * 등록/크롤링 작업 완료 시 전송된다.
 */
public record TaskCallbackPayload(
        String taskId,
        String status,                // SUCCESS / FAILED
        String platformProductId,     // 등록 성공 시 플랫폼 상품 ID
        String errorMessage,
        String screenshotPath,
        String completedAt,
        // 크롤링 완료 시 products 필드도 포함
        java.util.List<CrawledProductDto> products
) {
    public record CrawledProductDto(
            String cafe24ProductId,
            String name,
            Integer originalPrice,
            Integer salePrice,
            String categoryName,
            String description,
            java.util.List<ImageDto> images,
            java.util.List<OptionDto> options
    ) {}

    public record ImageDto(String url, Integer order, String type) {}

    public record OptionDto(
            String groupName, String value,
            Integer additionalPrice, Integer stockQty
    ) {}
}
