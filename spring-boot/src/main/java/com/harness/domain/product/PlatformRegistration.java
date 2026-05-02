package com.harness.domain.product;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "platform_registrations", indexes = {
    @Index(name = "idx_platform_reg_product_id", columnList = "product_id"),
    @Index(name = "idx_platform_reg_status",     columnList = "status"),
    @Index(name = "idx_platform_reg_platform",   columnList = "platform")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class PlatformRegistration {

    public enum Platform { COUPANG, NAVER, OHOUSE }

    public enum Status { PENDING, RUNNING, SUCCESS, FAILED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Platform platform;

    @Column(length = 100)
    private String taskId;

    @Column(length = 100)
    private String platformProductId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(length = 500)
    private String screenshotPath;

    private LocalDateTime registeredAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // ── 팩토리 ────────────────────────────────────────────────

    public static PlatformRegistration create(Product product, Platform platform) {
        PlatformRegistration reg = new PlatformRegistration();
        reg.product  = product;
        reg.platform = platform;
        reg.status   = Status.PENDING;
        return reg;
    }

    // ── 상태 전이 ─────────────────────────────────────────────

    public void start(String taskId) {
        this.taskId = taskId;
        this.status = Status.RUNNING;
    }

    public void succeed(String platformProductId) {
        this.platformProductId = platformProductId;
        this.status            = Status.SUCCESS;
        this.registeredAt      = LocalDateTime.now();
    }

    public void fail(String errorMessage, String screenshotPath) {
        this.errorMessage   = errorMessage;
        this.screenshotPath = screenshotPath;
        this.status         = Status.FAILED;
    }

    public void resetForRetry() {
        this.status         = Status.PENDING;
        this.errorMessage   = null;
        this.screenshotPath = null;
        this.taskId         = null;
    }
}
