package com.finvibe.gateway.tokenfamily.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class TokenFamilyPolicyTests {

    private final TokenFamilyPolicy tokenFamilyPolicy = new TokenFamilyPolicy(
            Clock.fixed(Instant.parse("2026-04-10T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void allowsActiveTokenFamilyBeforeExpiration() {
        TokenFamilySnapshot snapshot = new TokenFamilySnapshot(
                "family-active",
                TokenFamilyStatus.ACTIVE,
                Instant.parse("2026-05-10T00:00:00Z"));

        assertThat(tokenFamilyPolicy.validate(snapshot)).isEqualTo(TokenFamilyValidationResult.ALLOW);
    }

    @Test
    void deniesActiveTokenFamilyWhenExpirationHasPassed() {
        TokenFamilySnapshot snapshot = new TokenFamilySnapshot(
                "family-expired",
                TokenFamilyStatus.ACTIVE,
                Instant.parse("2026-04-01T00:00:00Z"));

        assertThat(tokenFamilyPolicy.validate(snapshot)).isEqualTo(TokenFamilyValidationResult.DENY_UNAUTHORIZED);
    }

    @Test
    void deniesInvalidatedTokenFamily() {
        TokenFamilySnapshot snapshot = new TokenFamilySnapshot(
                "family-invalidated",
                TokenFamilyStatus.INVALIDATED,
                Instant.parse("2026-05-10T00:00:00Z"));

        assertThat(tokenFamilyPolicy.validate(snapshot)).isEqualTo(TokenFamilyValidationResult.DENY_UNAUTHORIZED);
    }
}
