package com.finvibe.gateway;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

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
        properties = "finvibe.gateway.token-family.cache-enabled=false")
class GatewaySecurityCacheDisabledIntegrationTests {

    private static final String JWT_SECRET = "finvibe-gateway-test-secret-1234567890";
    private static final String ACTIVE_FAMILY_ID = "family-active";

    private static HttpServer downstreamServer;
    private static final AtomicInteger tokenFamilyLookupCount = new AtomicInteger();

    private WebTestClient webTestClient;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("finvibe.gateway.security.jwt.secret", () -> JWT_SECRET);
        registry.add("finvibe.gateway.services.websocket-listener-url", () -> "ws://localhost:18090");
        registry.add("finvibe.gateway.services.was-url", () -> "http://127.0.0.1:" + downstreamPort());
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
        tokenFamilyLookupCount.set(0);
        this.webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + port)
                .build();
    }

    @Test
    void bypassesRedisAndMemoryCacheWhenCacheIsDisabled() throws Exception {
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

        if (tokenFamilyLookupCount.get() != 2) {
            throw new AssertionError("Expected token family lookup count to be 2 but was " + tokenFamilyLookupCount.get());
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

        downstreamServer.createContext("/api/secured", GatewaySecurityCacheDisabledIntegrationTests::handleSecured);
        downstreamServer.createContext("/internal/auth/token-families", GatewaySecurityCacheDisabledIntegrationTests::handleTokenFamily);
        downstreamServer.start();
    }

    private static void handleSecured(HttpExchange exchange) throws IOException {
        byte[] body = "secured-ok".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain;charset=UTF-8");
        exchange.sendResponseHeaders(200, body.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private static void handleTokenFamily(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String prefix = "/internal/auth/token-families/";
        if (!path.startsWith(prefix)) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        String familyId = path.substring(prefix.length());
        tokenFamilyLookupCount.incrementAndGet();
        if (!ACTIVE_FAMILY_ID.equals(familyId)) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        byte[] body = ("{" +
                "\"familyId\":\"" + familyId + "\"," +
                "\"status\":\"ACTIVE\"," +
                "\"expiresAt\":\"2030-01-01T00:00:00Z\"" +
                "}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(200, body.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private static String createJwt(String secret, Instant expiresAt, String tokenFamilyId) throws JOSEException {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("gateway-test-user")
                .issuer("finvibe-test-suite")
                .issueTime(new Date())
                .expirationTime(Date.from(expiresAt))
                .claim("token_type", "access")
                .claim("token_family_id", tokenFamilyId)
                .build();

        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
        signedJwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return signedJwt.serialize();
    }
}
