package com.finvibe.gateway;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewaySecurityIntegrationTests {

    private static final String JWT_SECRET = "finvibe-gateway-test-secret-1234567890";

    private static HttpServer downstreamServer;

    private WebTestClient webTestClient;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("finvibe.gateway.security.jwt.secret", () -> JWT_SECRET);
        registry.add("finvibe.gateway.services.market-url", () -> "ws://localhost:18090");
        registry.add("finvibe.gateway.services.was-url", () -> "http://127.0.0.1:" + downstreamPort());
    }

    @AfterAll
    static void tearDown() {
        if (downstreamServer != null) {
            downstreamServer.stop(0);
        }
    }

    @BeforeEach
    void setUpClient() {
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
                .header(AUTHORIZATION, "Bearer " + createJwt(JWT_SECRET, Instant.now().plusSeconds(300)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("secured-ok");
    }

    @Test
    void rejectsProtectedApiWithInvalidJwtSignature() throws Exception {
        webTestClient.get()
                .uri("/api/secured")
                .header(AUTHORIZATION, "Bearer " + createJwt("another-test-secret-1234567890-abcdef", Instant.now().plusSeconds(300)))
                .exchange()
                .expectStatus().isUnauthorized();
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
        byte[] body = "secured-ok".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain;charset=UTF-8");
        exchange.sendResponseHeaders(200, body.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private static String createJwt(String secret, Instant expiresAt) throws JOSEException {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("gateway-test-user")
                .issuer("finvibe-test-suite")
                .issueTime(new Date())
                .expirationTime(Date.from(expiresAt))
                .build();

        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
        signedJwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return signedJwt.serialize();
    }
}
