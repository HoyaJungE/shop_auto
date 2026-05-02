package com.harness.repository.product;

import com.harness.domain.product.PlatformRegistration;
import com.harness.domain.product.PlatformRegistration.Platform;
import com.harness.domain.product.PlatformRegistration.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlatformRegistrationRepository extends JpaRepository<PlatformRegistration, Long> {

    Optional<PlatformRegistration> findByProductIdAndPlatform(Long productId, Platform platform);

    Optional<PlatformRegistration> findByTaskId(String taskId);

    List<PlatformRegistration> findByProductId(Long productId);

    List<PlatformRegistration> findByStatus(Status status);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE PlatformRegistration r
        SET r.status = 'PENDING'
        WHERE r.status = 'RUNNING'
        """)
    int resetStuckRunning();  // 서버 재시작 시 RUNNING 상태 초기화
}
