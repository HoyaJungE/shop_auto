package com.harness.service;

import com.harness.domain.product.PlatformRegistration;
import com.harness.domain.product.PlatformRegistration.Platform;
import com.harness.domain.product.PlatformRegistration.Status;
import com.harness.domain.product.Product;
import com.harness.repository.product.PlatformRegistrationRepository;
import com.harness.repository.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProductRepository productRepository;
    private final PlatformRegistrationRepository registrationRepository;

    @Transactional(readOnly = true)
    public StatsDto getStats() {
        long total     = productRepository.count();
        long done      = productRepository.countByStatus(Product.Status.DONE);
        long error     = productRepository.countByStatus(Product.Status.ERROR);
        long publishing = productRepository.countByStatus(Product.Status.PUBLISHING);
        long ready     = productRepository.countByStatus(Product.Status.READY);
        long raw       = productRepository.countByStatus(Product.Status.RAW);

        // 플랫폼별 성공/실패 집계
        List<PlatformRegistration> allRegs = registrationRepository.findAll();
        Map<String, PlatformStat> platformStats = new LinkedHashMap<>();

        for (Platform platform : Platform.values()) {
            long success = allRegs.stream()
                    .filter(r -> r.getPlatform() == platform && r.getStatus() == Status.SUCCESS)
                    .count();
            long failed = allRegs.stream()
                    .filter(r -> r.getPlatform() == platform && r.getStatus() == Status.FAILED)
                    .count();
            long running = allRegs.stream()
                    .filter(r -> r.getPlatform() == platform && r.getStatus() == Status.RUNNING)
                    .count();
            platformStats.put(platform.name(), new PlatformStat(success, failed, running));
        }

        return new StatsDto(total, done, error, publishing, ready, raw, platformStats);
    }

    // ── DTO ──────────────────────────────────────────────────────

    public record StatsDto(
            long total,
            long done,
            long error,
            long publishing,
            long ready,
            long raw,
            Map<String, PlatformStat> platformStats
    ) {}

    public record PlatformStat(
            long success,
            long failed,
            long running
    ) {}
}
