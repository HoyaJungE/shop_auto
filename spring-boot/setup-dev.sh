#!/bin/bash
# 개발 환경 설정 스크립트
# 처음 한 번만 실행하면 됩니다.

echo "=== harness 개발 환경 설정 ==="

# Java 21 확인
JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VER" -lt "21" ] 2>/dev/null; then
    echo "❌ Java 21 이상 필요 (현재: $JAVA_VER)"
    echo "   다운로드: https://adoptium.net/"
    exit 1
fi
echo "✅ Java $JAVA_VER 확인"

# gradle-wrapper.jar 다운로드
if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
    echo "📥 gradle-wrapper.jar 다운로드 중..."
    curl -L "https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar" \
         -o gradle/wrapper/gradle-wrapper.jar
    echo "✅ gradle-wrapper.jar 다운로드 완료"
fi

# Gradle 테스트 실행
echo "🧪 테스트 실행 중..."
./gradlew test -Dspring.profiles.active=test
echo "✅ 완료"
