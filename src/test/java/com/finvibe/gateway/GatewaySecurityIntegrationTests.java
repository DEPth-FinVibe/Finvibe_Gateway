package com.finvibe.gateway;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.finvibe.gateway.tokenfamily.application.TokenFamilyReader;
import com.finvibe.gateway.tokenfamily.domain.TokenFamilySnapshot;
import com.finvibe.gateway.tokenfamily.domain.TokenFamilyStatus;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.allow-bean-definition-overriding=true")
class GatewaySecurityIntegrationTests {

    private static final String JWT_SECRET = "finvibe-gateway-test-secret-1234567890";
    private static final String ACTIVE_FAMILY_ID = "family-active";
    private static final String INVALIDATED_FAMILY_ID = "family-invalidated";
    private static final String EXPIRED_FAMILY_ID = "family-expired";
    private static final String UNAVAILABLE_FAMILY_ID = "family-unavailable";

    private static HttpServer downstreamServer;
    private static final ConcurrentHashMap<String, TokenFamilySnapshot> tokenFamilyStatuses = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> tokenFamilyLookupCounts = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CachedResponse> tokenFamilyResponseCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, List<String>> downstreamRequestHeaders = new ConcurrentHashMap<>();

    private WebTestClient webTestClient;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("finvibe.gateway.security.jwt.secret", () -> JWT_SECRET);
        registry.add("finvibe.gateway.services.websocket-listener-url", () -> "ws://localhost:18090");
        registry.add("finvibe.gateway.services.was-url", () -> "http://127.0.0.1:" + downstreamPort());
        registry.add("finvibe.gateway.token-family.cache-ttl", () -> "PT30S");
        registry.add("spring.cloud.gateway.server.webflux.trusted-proxies", () -> "127\\.0\\.0\\.1|::1");
    }

    @AfterAll
    static void tearDown() {
        if (downstreamServer != null) {
            downstreamServer.stop(0);
        }
    }

    @BeforeEach
    void setUpClient() {
        tokenFamilyStatuses.clear();
        tokenFamilyLookupCounts.clear();
        tokenFamilyResponseCache.clear();
        downstreamRequestHeaders.clear();
        tokenFamilyStatuses.put(ACTIVE_FAMILY_ID, new TokenFamilySnapshot(ACTIVE_FAMILY_ID, TokenFamilyStatus.ACTIVE,
                Instant.parse("2030-01-01T00:00:00Z")));
        tokenFamilyStatuses.put(INVALIDATED_FAMILY_ID,
                new TokenFamilySnapshot(INVALIDATED_FAMILY_ID, TokenFamilyStatus.INVALIDATED,
                        Instant.parse("2030-01-01T00:00:00Z")));
        tokenFamilyStatuses.put(EXPIRED_FAMILY_ID, new TokenFamilySnapshot(EXPIRED_FAMILY_ID, TokenFamilyStatus.ACTIVE,
                Instant.parse("2020-01-01T00:00:00Z")));
        this.webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + port)
                .build();
    }

    @Test
    void rejectsProtectedApiWithoutJwt() {
        webTestClient.get()
                .uri("/api/secured")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void allowsPublicGatewayEndpointWithoutJwt() {
        webTestClient.get()
                .uri("/gateway/info")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.application").isEqualTo("finvibe-gateway");
    }

    @Test
    void forwardsProtectedApiWithValidJwt() throws Exception {
        webTestClient.get()
                .uri("/api/secured")
                .header(AUTHORIZATION, "Bearer " + createJwt(JWT_SECRET, Instant.now().plusSeconds(300), ACTIVE_FAMILY_ID))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("secured-ok");
    }

    @Test
    void rejectsProtectedApiWithInvalidJwtSignature() throws Exception {
        webTestClient.get()
                .uri("/api/secured")
                .header(AUTHORIZATION,
                        "Bearer " + createJwt("another-test-secret-1234567890-abcdef", Instant.now().plusSeconds(300),
                                ACTIVE_FAMILY_ID))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void rejectsProtectedApiWhenTokenFamilyIsInvalidated() throws Exception {
        webTestClient.get()
                .uri("/api/secured")
                .header(AUTHORIZATION, "Bearer " + createJwt(JWT_SECRET, Instant.now().plusSeconds(300), INVALIDATED_FAMILY_ID))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void rejectsProtectedApiWhenTokenFamilyClaimIsMissing() throws Exception {
        webTestClient.get()
                .uri("/api/secured")
                .header(AUTHORIZATION, "Bearer " + createJwtWithoutFamilyClaim(JWT_SECRET, Instant.now().plusSeconds(300)))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void returnsServiceUnavailableWhenTokenFamilyLookupFails() throws Exception {
        webTestClient.get()
                .uri("/api/secured")
                .header(AUTHORIZATION, "Bearer " + createJwt(JWT_SECRET, Instant.now().plusSeconds(300), UNAVAILABLE_FAMILY_ID))
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void rejectsProtectedApiWhenTokenFamilyIsExpiredByTimestamp() throws Exception {
        webTestClient.get()
                .uri("/api/secured")
                .header(AUTHORIZATION, "Bearer " + createJwt(JWT_SECRET, Instant.now().plusSeconds(300), EXPIRED_FAMILY_ID))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void cachesTokenFamilyStatusForRepeatedRequests() throws Exception {
        String jwt = createJwt(JWT_SECRET, Instant.now().plusSeconds(300), ACTIVE_FAMILY_ID);

        webTestClient.get()
                .uri("/api/secured")
                .header(AUTHORIZATION, "Bearer " + jwt)
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/api/secured")
                .header(AUTHORIZATION, "Bearer " + jwt)
                .exchange()
                .expectStatus().isOk();

        AtomicInteger count = tokenFamilyLookupCounts.get(ACTIVE_FAMILY_ID);
        if (count == null || count.get() != 1) {
            throw new AssertionError("Expected token family lookup count to be 1 but was " + (count == null ? 0 : count.get()));
        }
    }

    @Test
    void forwardsClientContextHeadersToDownstream() throws Exception {
        webTestClient.get()
                .uri("/api/secured")
                .header(AUTHORIZATION, "Bearer " + createJwt(JWT_SECRET, Instant.now().plusSeconds(300), ACTIVE_FAMILY_ID))
                .header("User-Agent", "Mozilla/5.0 TestBrowser")
                .header("X-Forwarded-For", "203.0.113.10")
                .exchange()
                .expectStatus().isOk();

        if (!"Mozilla/5.0 TestBrowser".equals(firstHeaderValue("User-Agent"))) {
            throw new AssertionError("Expected User-Agent to be forwarded but was " + firstHeaderValue("User-Agent"));
        }

        if (!"203.0.113.10".equals(firstHeaderValue("X-Forwarded-For"))) {
            throw new AssertionError(
                    "Expected X-Forwarded-For to preserve client IP chain but was " + firstHeaderValue("X-Forwarded-For"));
        }
    }

    private static int downstreamPort() {
        ensureDownstreamServer();
        return downstreamServer.getAddress().getPort();
    }

    private static synchronized void ensureDownstreamServer() {
        if (downstreamServer != null) {
            return;
        }

        try {
            downstreamServer = HttpServer.create(new InetSocketAddress(0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start downstream test server", e);
        }

        downstreamServer.createContext("/api/secured", GatewaySecurityIntegrationTests::handleSecured);
        downstreamServer.start();
    }

    private static void handleSecured(HttpExchange exchange) throws IOException {
        exchange.getRequestHeaders().forEach((name, values) -> downstreamRequestHeaders.put(name, List.copyOf(values)));
        byte[] body = "secured-ok".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain;charset=UTF-8");
        exchange.sendResponseHeaders(200, body.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private static String firstHeaderValue(String headerName) {
        for (Map.Entry<String, List<String>> entry : downstreamRequestHeaders.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(headerName) && !entry.getValue().isEmpty()) {
                return entry.getValue().getFirst();
            }
        }
        return null;
    }

    private static String createJwt(String secret, Instant expiresAt, String tokenFamilyId) throws JOSEException {
        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .subject("gateway-test-user")
                .issuer("finvibe-test-suite")
                .issueTime(new Date())
                .expirationTime(Date.from(expiresAt))
                .claim("token_type", "access");

        if (tokenFamilyId != null) {
            claimsBuilder.claim("token_family_id", tokenFamilyId);
        }

        JWTClaimsSet claimsSet = claimsBuilder.build();

        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
        signedJwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return signedJwt.serialize();
    }

    private static String createJwtWithoutFamilyClaim(String secret, Instant expiresAt) throws JOSEException {
        return createJwt(secret, expiresAt, null);
    }

    @TestConfiguration
    static class TestTokenFamilyStatusClientConfiguration {

        @Bean
        TokenFamilyReader tokenFamilyReader() {
            return familyId -> {
                CachedResponse cachedResponse = tokenFamilyResponseCache.get(familyId);
                if (cachedResponse != null && !cachedResponse.isExpired()) {
                    if (cachedResponse.error() != null) {
                        return reactor.core.publisher.Mono.error(cachedResponse.error());
                    }
                    if (cachedResponse.response() == null) {
                        return reactor.core.publisher.Mono.empty();
                    }
                    return reactor.core.publisher.Mono.just(cachedResponse.response());
                }

                tokenFamilyLookupCounts.computeIfAbsent(familyId, ignored -> new AtomicInteger()).incrementAndGet();
                if (UNAVAILABLE_FAMILY_ID.equals(familyId)) {
                    IllegalStateException error = new IllegalStateException("Redis unavailable");
                    tokenFamilyResponseCache.put(familyId,
                            new CachedResponse(null, error, Instant.now().plus(Duration.ofSeconds(30))));
                    return reactor.core.publisher.Mono.error(error);
                }

                TokenFamilySnapshot response = tokenFamilyStatuses.get(familyId);
                if (response == null) {
                    tokenFamilyResponseCache.put(familyId,
                            new CachedResponse(null, null, Instant.now().plus(Duration.ofSeconds(30))));
                    return reactor.core.publisher.Mono.empty();
                }

                tokenFamilyResponseCache.put(familyId,
                        new CachedResponse(response, null, Instant.now().plus(Duration.ofSeconds(30))));
                return reactor.core.publisher.Mono.just(response);
            };
        }
    }

    private record CachedResponse(
            TokenFamilySnapshot response,
            RuntimeException error,
            Instant expiresAt) {

        private boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
