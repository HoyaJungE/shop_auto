package com.harness.dto.registration;

import lombok.Builder;

import java.util.List;

@Builder
public record PlaywrightRegisterRequest(
        String taskId,
        String platform,
        ProductPayload product,
        Credentials credentials
) {
    @Builder
    public record ProductPayload(
            String cafe24ProductId,
            String name,
            Integer originalPrice,
            Integer salePrice,
            String description,
            List<ProductImage> images,
            List<ProductOption> options,
            String categoryCode
    ) {}

    public record ProductImage(String url, Integer order, String type) {}

    public record ProductOption(
            String groupName, String value,
            Integer additionalPrice, Integer stockQty
    ) {}

    public record Credentials(String loginId, String password) {}
}
