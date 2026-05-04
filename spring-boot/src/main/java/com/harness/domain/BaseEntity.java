package com.harness.domain;

import jakarta.persistence.*;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 모든 Entity의 공통 기반 클래스
 * - id (PK, AUTO_INCREMENT)
 * - createdAt, updatedAt (Auditing 자동 관리)
 *
 * 사용법: @Entity 클래스에서 extends BaseEntity
 * 활성화: @EnableJpaAuditing (Application 메인 클래스에 추가)
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "harness_seq")
    @SequenceGenerator(name = "harness_seq", sequenceName = "HARNESS_SEQ", allocationSize = 50)
    @Column(name = "id", columnDefinition = "NUMBER(19)")
    private Long id;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
