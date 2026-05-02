package com.harness.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * ③ API Gateway: JWT 인증 필터
 *
 * [역할]
 * - 모든 보호 라우트에서 Bearer 토큰 검증
 * - 검증 성공 시 X-User-Id, X-User-Role 헤더를 하위 서비스에 전달
 *   → 각 서비스는 DB 조회 없이 헤더만으로 사용자 식별 가능
 *
 * [Gateway 필터 적용 방식]
 * application.yml 라우트의 filters: - AuthFilter
 */
@Slf4j
@Component
public class AuthFilter extends AbstractGatewayFilterFactory<AuthFilter.Config> {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.issuer}")
    private String issuer;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public AuthFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return unauthorized(exchange, "AUTH_004", "Authorization 헤더가 없습니다.");
            }

            String token = authHeader.substring(7);
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(key)
                        .requireIssuer(issuer)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                // token_type 검증: access 토큰만 허용
                if (!"access".equals(claims.get("token_type"))) {
                    return unauthorized(exchange, "AUTH_002", "유효하지 않은 토큰입니다.");
                }

                // 하위 서비스로 사용자 정보 헤더 전달
                ServerWebExchange mutated = exchange.mutate()
                        .request(r -> r.headers(headers -> {
                            headers.set("X-User-Id", claims.getSubject());
                            headers.set("X-User-Role", claims.get("role", String.class));
                        }))
                        .build();

                return chain.filter(mutated);

            } catch (ExpiredJwtException e) {
                return unauthorized(exchange, "AUTH_001", "토큰이 만료되었습니다.");
            } catch (Exception e) {
                return unauthorized(exchange, "AUTH_002", "유효하지 않은 토큰입니다.");
            }
        };
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String code, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"success\":false,\"error\":{\"code\":\"%s\",\"message\":\"%s\"}}",
                code, message);
        DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    public static class Config {}
}
