package com.harness.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiter (Bucket4j 기반, IP 단위)
 *
 * 기본: 로그인 엔드포인트에 대해 60초당 5회 제한
 * Redis 없이 ConcurrentHashMap으로 인메모리 관리
 * → 멀티 인스턴스 운영 시 Redis + Bucket4j-Redis 로 교체
 *
 * 사용 예 (Controller 또는 Service):
 *   if (!rateLimitConfig.tryConsume(clientIp)) {
 *       throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
 *   }
 */
@Component
public class RateLimitConfig {

    @Value("${rate-limit.login.capacity:5}")
    private int capacity;

    @Value("${rate-limit.login.refill-tokens:5}")
    private int refillTokens;

    @Value("${rate-limit.login.refill-seconds:60}")
    private int refillSeconds;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, this::newBucket);
        return bucket.tryConsume(1);
    }

    private Bucket newBucket(String key) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(refillTokens, Duration.ofSeconds(refillSeconds))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
