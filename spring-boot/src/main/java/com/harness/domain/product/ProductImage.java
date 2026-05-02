package com.harness.domain.product;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_images",
    indexes = @Index(name = "idx_product_images_product_id", columnList = "product_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ProductImage {

    public enum ImageType { REPRESENTATIVE, DETAIL }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 1000)
    private String imageUrl;

    @Column(nullable = false)
    private Integer imageOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImageType imageType;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public static ProductImage of(String imageUrl, Integer imageOrder, ImageType imageType) {
        ProductImage img = new ProductImage();
        img.imageUrl = imageUrl;
        img.imageOrder = imageOrder != null ? imageOrder : 0;
        img.imageType = imageType != null ? imageType : ImageType.DETAIL;
        return img;
    }

    void assignProduct(Product product) { this.product = product; }
}
