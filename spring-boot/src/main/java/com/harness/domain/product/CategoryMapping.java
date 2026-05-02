package com.harness.domain.product;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "category_mappings",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_category_mappings_cafe24_platform",
        columnNames = {"cafe24Category", "platform"}
    ),
    indexes = {
        @Index(name = "idx_category_mappings_cafe24",    columnList = "cafe24Category"),
        @Index(name = "idx_category_mappings_confirmed", columnList = "confirmed")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CategoryMapping {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String cafe24Category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlatformRegistration.Platform platform;

    @Column(nullable = false, length = 100)
    private String platformCategoryId;

    @Column(length = 200)
    private String platformCategoryName;

    /**
     * 관리자가 AI 제안을 검토 후 확인한 경우 true.
     * false 상태의 매핑은 등록 전 확인이 필요하다.
     */
    @Column(nullable = false)
    private boolean confirmed = false;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public static CategoryMapping create(
            String cafe24Category,
            PlatformRegistration.Platform platform,
            String platformCategoryId,
            String platformCategoryName
    ) {
        CategoryMapping m = new CategoryMapping();
        m.cafe24Category       = cafe24Category;
        m.platform             = platform;
        m.platformCategoryId   = platformCategoryId;
        m.platformCategoryName = platformCategoryName;
        m.confirmed            = false;
        return m;
    }

    public void confirm() { this.confirmed = true; }

    public void update(String platformCategoryId, String platformCategoryName) {
        this.platformCategoryId   = platformCategoryId;
        this.platformCategoryName = platformCategoryName;
        this.confirmed            = false;  // 변경 시 재확인 필요
    }
}
