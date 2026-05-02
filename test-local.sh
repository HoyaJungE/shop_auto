#!/bin/bash
# ─────────────────────────────────────────────────────────────────
# 로컬 환경 전체 테스트 실행 스크립트
# 
# 사전 요구사항:
#   - Docker (Spring Boot 테스트용)
#   - Node.js 18+ (playwright-service 테스트용)
#   - 또는 JDK 17+ + Gradle (Docker 없이 Spring Boot 테스트)
# ─────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo_ok()  { echo -e "${GREEN}✅ $1${NC}"; }
echo_err() { echo -e "${RED}❌ $1${NC}"; }
echo_warn(){ echo -e "${YELLOW}⚠️  $1${NC}"; }

# ── playwright-service 테스트 ────────────────────────────────────
echo ""
echo "════════════════════════════════════════"
echo "  playwright-service 테스트 (Node.js)"
echo "════════════════════════════════════════"

if ! command -v node &>/dev/null; then
    echo_err "Node.js가 설치되어 있지 않습니다. Node.js 18 이상을 설치하세요."
    exit 1
fi

NODE_VER=$(node --version | sed 's/v//' | cut -d. -f1)
if [ "$NODE_VER" -lt 18 ]; then
    echo_err "Node.js 18 이상이 필요합니다. 현재: $(node --version)"
    exit 1
fi

echo "Node.js $(node --version) 확인됨"
cd playwright-service
npm ci --silent
npm test
echo_ok "playwright-service 테스트 완료"
cd ..

# ── Spring Boot 테스트 ────────────────────────────────────────────
echo ""
echo "════════════════════════════════════════"
echo "  Spring Boot 테스트 (Java 21)"
echo "════════════════════════════════════════"

# Docker 방식 우선, 없으면 로컬 JDK 사용
if command -v docker &>/dev/null && docker ps &>/dev/null 2>&1; then
    echo "Docker 사용하여 Spring Boot 테스트 실행..."
    docker run --rm \
        -v "$(pwd)/spring-boot:/workspace" \
        -w /workspace \
        -e JAVA_TOOL_OPTIONS="-Xmx2g" \
        eclipse-temurin:21-jdk-jammy \
        bash -c "chmod +x gradlew && ./gradlew test -Dspring.profiles.active=test --no-daemon"
    echo_ok "Spring Boot 테스트 완료 (Docker)"
    echo "테스트 결과: spring-boot/build/reports/tests/test/index.html"
elif command -v java &>/dev/null; then
    JAVA_VER=$(java -version 2>&1 | grep version | sed 's/.*version "\([0-9]*\).*/\1/')
    if [ "$JAVA_VER" -ge 17 ]; then
        echo "로컬 JDK $(java -version 2>&1 | head -1) 사용..."
        cd spring-boot
        chmod +x gradlew
        ./gradlew test -Dspring.profiles.active=test
        echo_ok "Spring Boot 테스트 완료 (로컬 JDK)"
        echo "테스트 결과: build/reports/tests/test/index.html"
        cd ..
    else
        echo_warn "JDK 17 이상이 필요합니다. 현재: Java $JAVA_VER"
        echo_warn "Docker 설치 또는 JDK 21 설치 후 재실행하세요."
        echo_warn "또는 GitHub Actions에서 자동으로 테스트가 실행됩니다."
    fi
else
    echo_warn "Docker 및 JDK를 찾을 수 없습니다."
    echo_warn "다음 중 하나를 설치하세요:"
    echo_warn "  1. Docker Desktop: https://www.docker.com/products/docker-desktop"
    echo_warn "  2. JDK 21: https://adoptium.net/temurin/releases/"
    echo_warn "CI/CD: GitHub Actions에서 자동으로 테스트가 실행됩니다 (push시)"
fi

echo ""
echo_ok "테스트 스크립트 완료"
