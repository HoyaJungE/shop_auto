# ─────────────────────────────────────────────────────────────────
# Harness Makefile — 로컬·CI 테스트 실행기
# ─────────────────────────────────────────────────────────────────

.PHONY: all test test-playwright test-spring test-spring-docker \
        build run stop clean help

# ── 기본 타겟
all: test

# ── 전체 테스트 (로컬 JDK + Gradle 설치 필요)
test: test-playwright test-spring
	@echo "✅ 전체 테스트 완료"

# ── playwright-service 테스트 (Node.js 환경에서 실행 가능)
test-playwright:
	@echo "▶ playwright-service 테스트 실행..."
	cd playwright-service && npm ci && npm test

test-playwright-coverage:
	@echo "▶ playwright-service 커버리지 수집..."
	cd playwright-service && npm ci && npm run test:coverage

# ── Spring Boot 테스트 (JDK 17+ 및 Gradle 필요)
test-spring:
	@echo "▶ Spring Boot 테스트 실행..."
	cd spring-boot && chmod +x gradlew && \
	  ./gradlew test -Dspring.profiles.active=test
	@echo "📊 테스트 결과: spring-boot/build/reports/tests/test/index.html"

# ── Spring Boot 테스트 — Docker 방식 (JDK 불필요)
test-spring-docker:
	@echo "▶ Docker로 Spring Boot 테스트 실행..."
	docker run --rm \
	  -v "$$(pwd)/spring-boot:/workspace" \
	  -w /workspace \
	  eclipse-temurin:21-jdk \
	  bash -c "chmod +x gradlew && ./gradlew test -Dspring.profiles.active=test"
	@echo "📊 테스트 결과: spring-boot/build/reports/tests/test/index.html"

# ── 프로덕션 빌드
build:
	@echo "▶ 프로덕션 빌드..."
	cd spring-boot && chmod +x gradlew && ./gradlew bootJar -x test
	cd playwright-service && npm ci && npm run build

# ── 로컬 개발 환경 실행 (Docker Compose)
run:
	docker-compose up -d
	@echo "✅ 서비스 시작됨"
	@echo "  - Spring Boot: http://localhost:8080"
	@echo "  - playwright-service: http://localhost:3100"

# ── 서비스 중지
stop:
	docker-compose down

# ── 빌드 산출물 정리
clean:
	cd spring-boot && chmod +x gradlew && ./gradlew clean
	rm -rf playwright-service/dist playwright-service/coverage

# ── 도움말
help:
	@echo "사용 가능한 타겟:"
	@echo "  make test                  - 전체 테스트 실행"
	@echo "  make test-playwright       - playwright-service 테스트만"
	@echo "  make test-spring           - Spring Boot 테스트만 (JDK 17+ 필요)"
	@echo "  make test-spring-docker    - Spring Boot 테스트 (Docker 사용)"
	@echo "  make build                 - 프로덕션 빌드"
	@echo "  make run                   - 로컬 Docker 환경 시작"
	@echo "  make stop                  - Docker 환경 중지"
	@echo "  make clean                 - 빌드 산출물 정리"
