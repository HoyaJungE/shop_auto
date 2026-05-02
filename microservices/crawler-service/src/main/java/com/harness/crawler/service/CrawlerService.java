package com.harness.crawler.service;

import com.harness.events.DataCollectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ③ crawler-service: 웹 크롤링 서브에이전트
 *
 * [크롤링 전략]
 * - 정적 HTML (뉴스, 블로그 등): Jsoup (빠르고 가벼움)
 * - 동적 JS 렌더링 필요 시: playwright-enabled=true 로 전환
 *
 * [데이터 흐름]
 * CrawlerService.crawlAll()
 *   └── 각 URL 크롤링 → DB 저장 → Kafka DataCollectedEvent 발행
 *         └── analyzer-service가 수신해 AI 분석 수행
 *
 * [스케줄]
 * 매 시간 실행 (cron 조정 가능)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerService {

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, DataCollectedEvent> kafkaTemplate;

    @Value("${crawler.user-agent}")
    private String userAgent;

    @Value("${crawler.timeout-ms:10000}")
    private int timeoutMs;

    @Value("${crawler.max-retries:3}")
    private int maxRetries;

    @Value("${topics.data-collected}")
    private String dataCollectedTopic;

    /**
     * 스케줄: 매 시간 정각 크롤링 실행
     * 크롤 대상 URL은 DB의 crawl_target 테이블에서 관리
     */
    @Scheduled(cron = "0 0 * * * *")
    public void crawlAll() {
        List<Map<String, Object>> targets = fetchTargets();
        log.info("[Crawler] 크롤링 시작 - 대상 {}건", targets.size());

        for (Map<String, Object> target : targets) {
            String url = (String) target.get("url");
            String category = (String) target.get("category");
            try {
                crawlOne(url, category);
            } catch (Exception e) {
                log.error("[Crawler] 실패 url={}: {}", url, e.getMessage());
            }
        }
        log.info("[Crawler] 크롤링 완료");
    }

    private void crawlOne(String url, String category) throws Exception {
        // Jsoup으로 정적 HTML 파싱
        Document doc = Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(timeoutMs)
                .get();

        String title   = doc.title();
        String text    = doc.body().text();
        String payload = buildPayload(url, title, text, category);

        // DB 저장
        Long dataId = saveToDb(url, payload);

        // Kafka 이벤트 발행 → analyzer-service가 AI 분석 수행
        DataCollectedEvent event = new DataCollectedEvent(
                UUID.randomUUID().toString(),
                dataId,
                "crawl",
                "crawler-service",
                LocalDateTime.now()
        );
        kafkaTemplate.send(dataCollectedTopic, url, event);

        log.debug("[Crawler] 완료 url={}, dataId={}", url, dataId);
    }

    private String buildPayload(String url, String title, String text, String category) {
        // 실제로는 ObjectMapper 사용 권장
        String safeText = text.length() > 3000 ? text.substring(0, 3000) : text;
        return String.format(
                "{\"url\":\"%s\",\"title\":\"%s\",\"category\":\"%s\",\"content\":\"%s\"}",
                url, title.replace("\"", "'"), category,
                safeText.replace("\"", "'").replace("\n", " ")
        );
    }

    private Long saveToDb(String url, String payload) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO collected_data (client_id, data_type, payload, source_url, collected_at, status)
                VALUES ('crawler-service', 'crawl', ?::jsonb, ?, NOW(), 'RAW')
                RETURNING id
                """,
                Long.class, payload, url
        );
    }

    private List<Map<String, Object>> fetchTargets() {
        return jdbcTemplate.queryForList(
                "SELECT url, category FROM crawl_target WHERE is_active = true ORDER BY priority ASC"
        );
    }
}
