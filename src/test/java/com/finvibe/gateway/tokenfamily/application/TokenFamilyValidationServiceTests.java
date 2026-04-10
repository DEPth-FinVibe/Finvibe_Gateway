package com.finvibe.gateway.tokenfamily.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.finvibe.gateway.tokenfamily.domain.TokenFamilyPolicy;
import com.finvibe.gateway.tokenfamily.domain.TokenFamilySnapshot;
import com.finvibe.gateway.tokenfamily.domain.TokenFamilyStatus;
import com.finvibe.gateway.tokenfamily.domain.TokenFamilyValidationResult;

import reactor.core.publisher.Mono;

class TokenFamilyValidationServiceTests {

    private final TokenFamilyPolicy tokenFamilyPolicy = new TokenFamilyPolicy(
            Clock.fixed(Instant.parse("2026-04-10T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void returnsAllowWhenReaderFindsActiveFamily() {
        TokenFamilyReader tokenFamilyReader = familyId -> Mono.just(new TokenFamilySnapshot(
                familyId,
                TokenFamilyStatus.ACTIVE,
                Instant.parse("2026-05-10T00:00:00Z")));
        TokenFamilyValidationService service = new TokenFamilyValidationService(tokenFamilyReader, tokenFamilyPolicy);

        TokenFamilyValidationResult result = service.validate("family-active").block();

        assertThat(result).isEqualTo(TokenFamilyValidationResult.ALLOW);
    }

    @Test
    void returnsServiceUnavailableWhenReaderReturnsEmpty() {
        TokenFamilyReader tokenFamilyReader = familyId -> Mono.empty();
        TokenFamilyValidationService service = new TokenFamilyValidationService(tokenFamilyReader, tokenFamilyPolicy);

        TokenFamilyValidationResult result = service.validate("missing-family").block();

        assertThat(result).isEqualTo(TokenFamilyValidationResult.DENY_SERVICE_UNAVAILABLE);
    }

    @Test
    void returnsServiceUnavailableWhenReaderFails() {
        TokenFamilyReader tokenFamilyReader = familyId -> Mono.error(new IllegalStateException("Redis down"));
        TokenFamilyValidationService service = new TokenFamilyValidationService(tokenFamilyReader, tokenFamilyPolicy);

        TokenFamilyValidationResult result = service.validate("broken-family").block();

        assertThat(result).isEqualTo(TokenFamilyValidationResult.DENY_SERVICE_UNAVAILABLE);
    }
}
