package com.harness;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Harness 애플리케이션 진입점
 *
 * @EnableJpaAuditing  : BaseEntity의 createdAt / updatedAt 자동 관리
 * @EnableScheduling   : TokenService의 만료 토큰 정리 스케줄러 활성화
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class HarnessApplication {

    public static void main(String[] args) {
        SpringApplication.run(HarnessApplication.class, args);
    }
}
