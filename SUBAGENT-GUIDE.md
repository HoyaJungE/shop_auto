# 서브에이전트 구성 가이드

## 전체 아키텍처

```
외부 클라이언트 (React Web / Windows App)
         │
         ▼
┌─────────────────────────────────────────┐
│         API Gateway  :8080              │
│  - JWT 검증 (AuthFilter)                │
│  - Rate Limiting (Redis)                │
│  - Circuit Breaker (Resilience4j)       │
│  - 라우팅 → 각 서비스                    │
└──────┬──────────┬──────────┬────────────┘
       │          │          │
       ▼          ▼          ▼
  collector   analyzer   crawler
  :8081       :8082       :8083
       │          ▲          │
       └──────────┼──────────┘
          Kafka 이벤트 버스
          ├─ harness.data.collected
          └─ harness.analysis.completed
                   │
                   ▼
            ① Claude AI Agent
            (analyzer-service 내장)
                   │
                   ▼
            PostgreSQL / MinIO
```

---

## ① AI Agent (Claude API)

### 위치
`spring-boot/src/main/java/com/harness/agent/`

### 구성 파일
| 파일 | 역할 |
|------|------|
| `config/ClaudeConfig.java` | RestClient 빈 설정 |
| `dto/ClaudeRequest.java` | API 요청 DTO |
| `dto/ClaudeResponse.java` | API 응답 DTO |
| `service/ClaudeAgentService.java` | 4개 서브에이전트 태스크 |

### 서브에이전트 태스크
```java
// 이상 탐지
claudeAgentService.analyzeAnomaly(rawData);

// 보고서 생성
claudeAgentService.generateReport(data, "daily");

// 콘텐츠 분류 (크롤링 데이터)
claudeAgentService.classifyContent(content);

// 자유 형식 쿼리
claudeAgentService.query(systemPrompt, userMessage);
```

### 환경변수 설정 (필수)
```yaml
# application.yml
claude:
  api-key: ${CLAUDE_API_KEY}   # Anthropic 콘솔에서 발급
  model: claude-opus-4-5
  max-tokens: 2048
```

### 확장: Orchestrator 패턴
```
ClaudeAgentService (Orchestrator)
  ├── analyzeAnomaly()    ← 이상 탐지 서브에이전트
  ├── generateReport()    ← 보고서 생성 서브에이전트
  ├── classifyContent()   ← 분류 서브에이전트
  └── query()             ← 범용 서브에이전트
```
복잡한 작업은 여러 태스크를 순차/병렬 조합해 처리한다.

---

## ② Spring Batch 워커

### 위치
`spring-boot/src/main/java/com/harness/batch/`

### 구성 파일
| 파일 | 역할 |
|------|------|
| `reader/CollectedDataReader.java` | DB에서 RAW 데이터 페이지 단위 읽기 |
| `processor/AnalysisProcessor.java` | Claude AI 분석 위임 (①과 연결) |
| `writer/ResultWriter.java` | 결과 DB 저장 + 상태 업데이트 |
| `config/BatchJobConfig.java` | Job/Step 설정 (faultTolerant, retry) |
| `config/BatchScheduler.java` | Cron 스케줄 실행 |

### 처리 흐름
```
Scheduler (매 시간)
    ↓
JobLauncher → analysisJob
    ↓
analysisStep (Chunk=100)
    ├── CollectedDataReader   → DB에서 100건씩 읽기
    ├── AnalysisProcessor     → Claude API 분석 (①)
    └── ResultWriter          → DB 저장, status='PROCESSED'
```

### 핵심 설정
```java
// BatchJobConfig.java
.<Object[], AnalysisResult>chunk(100, transactionManager)
.faultTolerant()
.skipLimit(10)       // 최대 10건 에러 스킵 후 계속
.retryLimit(3)       // 네트워크 오류 시 3회 재시도
```

### 수동 실행 (API)
```bash
# 수동 트리거 (Controller 추가 후)
POST /api/v1/admin/batch/analysis/run
Authorization: Bearer {admin-token}
```

---

## ③ 마이크로서비스

### 위치
`microservices/`

### 서비스 목록
| 서비스 | 포트 | 역할 |
|--------|------|------|
| `api-gateway` | 8080 | 단일 진입점, JWT 검증, Circuit Breaker |
| `collector-service` | 8081 | Windows 클라이언트 데이터 수신 |
| `analyzer-service` | 8082 | Kafka 소비 + AI 분석 (① 포함) |
| `crawler-service` | 8083 | 웹 크롤링 + Kafka 발행 |

### Kafka 토픽
| 토픽 | 발행자 | 소비자 |
|------|--------|--------|
| `harness.data.collected` | collector, crawler | analyzer |
| `harness.analysis.completed` | analyzer | (알림, 대시보드 등) |

### 실행 방법

```bash
# 1. 인프라만 먼저 실행 (항상 필요)
docker compose up postgres redis kafka kafka-ui minio -d

# 2a. 모노리스 모드 (개발 초기)
docker compose --profile monolith up app -d

# 2b. 마이크로서비스 모드 (분리 후)
docker compose --profile microservices up -d

# Kafka UI 확인
open http://localhost:9090
```

### API Gateway 라우팅
```
[공개]  /api/v1/auth/**      → collector-service (Rate Limit 5req/60s)
[JWT]   /api/v1/collect/**   → collector-service
[JWT]   /api/v1/analysis/**  → analyzer-service
[ADMIN] /api/v1/crawl/**     → crawler-service
```

### 하위 서비스 헤더 전달
Gateway가 JWT 검증 후 하위 서비스에 헤더를 주입한다.
각 서비스는 DB 조회 없이 헤더만으로 사용자 식별이 가능하다.
```
X-User-Id:   {userId}
X-User-Role: {role}
```

---

## ①②③ 통합 흐름 예시

**크롤링 데이터가 AI 분석까지 도달하는 전체 경로:**

```
1. crawler-service
   └── CrawlerService.crawlOne(url)
         ├── Jsoup으로 HTML 파싱
         ├── DB INSERT (status='RAW')
         └── Kafka 발행 → harness.data.collected

2. analyzer-service (Kafka 소비)
   └── AnalyzerConsumer.consume(event)
         └── AnalyzerService.analyze(event)
               └── ClaudeAgentService.classifyContent(payload)  ← ①
                     └── Claude API 호출
                           ↓
               └── DB 저장 (status='PROCESSED')
               └── Kafka 발행 → harness.analysis.completed

3. (선택) 웹 대시보드
   └── analysis.completed 이벤트 수신
         → 실시간 알림 (SSE/WebSocket)
```

---

## 모노리스 → 마이크로서비스 전환 전략

지금 당장 마이크로서비스로 갈 필요는 없다. 아래 순서가 현실적이다.

```
1단계: 모노리스 + ① AI Agent
   spring-boot 단일 서비스에 ClaudeAgentService만 추가

2단계: 모노리스 + ① + ②
   Spring Batch 워커 추가 (같은 JVM 내)

3단계: 크롤러 분리
   트래픽/스케줄 독립 필요 시 crawler-service 먼저 분리

4단계: analyzer 분리
   AI 비용 / 처리량 모니터링 후 analyzer-service 분리

5단계: API Gateway 도입
   서비스가 3개 이상 분리된 시점에 게이트웨이 추가
```

---

## 트러블슈팅

| 증상 | 원인 | 해결 |
|------|------|------|
| Claude API 401 | API Key 미설정 | `CLAUDE_API_KEY` 환경변수 확인 |
| Batch Job 미실행 | `spring.batch.job.enabled=false` | 스케줄러 또는 수동 API로 실행 |
| Kafka 메시지 유실 | ack 누락 | `AnalyzerConsumer`의 `ack.acknowledge()` 위치 확인 |
| Gateway 502 | 하위 서비스 미기동 | `docker compose ps`로 서비스 상태 확인 |
| Circuit Breaker OPEN | 하위 서비스 반복 실패 | 10초 대기 후 HALF-OPEN 상태로 자동 복구 |
