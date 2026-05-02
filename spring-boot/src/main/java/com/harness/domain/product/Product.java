package com.harness.domain.product;

import com.harness.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_products_status", columnList = "status"),
    @Index(name = "idx_products_cafe24_id", columnList = "cafe24ProductId")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = {"images", "options", "registrations"})
public class Product extends BaseEntity {

    public enum Status {
        RAW,        // Cafe24 수집만 된 상태
        READY,      // 카테고리 매핑 완료, 등록 준비
        PUBLISHING, // 등록 진행 중
        DONE,       // 모든 플랫폼 등록 완료
        ERROR       // 오류 발생
    }

    @Column(unique = true)
    private String cafe24ProductId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private Integer originalPrice;

    @Column(nullable = false)
    private Integer salePrice;

    @Column(length = 100)
    private String categoryName;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.RAW;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("imageOrder ASC")
    private List<ProductImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductOption> options = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlatformRegistration> registrations = new ArrayList<>();

    // ── 팩토리 메서드 ──────────────────────────────────────────

    @Builder
    public static Product create(
            String cafe24ProductId,
            String name,
            Integer originalPrice,
            Integer salePrice,
            String categoryName,
            String description
    ) {
        Product p = new Product();
        p.cafe24ProductId = cafe24ProductId;
        p.name = name;
        p.originalPrice = originalPrice != null ? originalPrice : 0;
        p.salePrice = salePrice;
        p.categoryName = categoryName;
        p.description = description;
        p.status = Status.RAW;
        return p;
    }

    // ── 상태 변경 ──────────────────────────────────────────────

    public void markReady()      { this.status = Status.READY; }
    public void markPublishing() { this.status = Status.PUBLISHING; }
    public void markDone()       { this.status = Status.DONE; }
    public void markError()      { this.status = Status.ERROR; }

    public void update(String name, Integer originalPrice, Integer salePrice, String description) {
        if (name != null) this.name = name;
        if (originalPrice != null) this.originalPrice = originalPrice;
        if (salePrice != null) this.salePrice = salePrice;
        if (description != null) this.description = description;
    }

    public void addImage(ProductImage image) {
        images.add(image);
        image.assignProduct(this);
    }

    public void addOption(ProductOption option) {
        options.add(option);
        option.assignProduct(this);
    }

    public void clearImages() { images.clear(); }
    public void clearOptions() { options.clear(); }
}
