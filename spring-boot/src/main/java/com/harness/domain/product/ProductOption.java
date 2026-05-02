package com.harness.domain.product;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_options",
    indexes = @Index(name = "idx_product_options_product_id", columnList = "product_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ProductOption {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 50)
    private String optionGroup;

    @Column(nullable = false, length = 100)
    private String optionValue;

    @Column(nullable = false)
    private Integer stockQty = 0;

    @Column(nullable = false)
    private Integer additionalPrice = 0;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public static ProductOption of(
            String optionGroup, String optionValue,
            Integer stockQty, Integer additionalPrice
    ) {
        ProductOption opt = new ProductOption();
        opt.optionGroup      = optionGroup;
        opt.optionValue      = optionValue;
        opt.stockQty         = stockQty != null ? stockQty : 0;
        opt.additionalPrice  = additionalPrice != null ? additionalPrice : 0;
        return opt;
    }

    void assignProduct(Product product) { this.product = product; }
}
