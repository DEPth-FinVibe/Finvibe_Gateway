package com.finvibe.gateway.tokenfamily.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.finvibe.gateway.tokenfamily.application.TokenFamilyReader;
import com.finvibe.gateway.tokenfamily.domain.TokenFamilySnapshot;
import com.finvibe.gateway.tokenfamily.domain.TokenFamilyStatus;

import reactor.core.publisher.Mono;

class FallbackTokenFamilyReaderTests {

    @Test
    void returnsPrimaryResultWithoutCallingFallback() {
        TokenFamilySnapshot snapshot = new TokenFamilySnapshot(
                "family-primary",
                TokenFamilyStatus.ACTIVE,
                Instant.parse("2030-01-01T00:00:00Z"));
        AtomicReference<String> fallbackLookup = new AtomicReference<>();
        AtomicReference<TokenFamilySnapshot> cachedSnapshot = new AtomicReference<>();

        TokenFamilyReader reader = new FallbackTokenFamilyReader(
                familyId -> Mono.just(snapshot),
                familyId -> {
                    fallbackLookup.set(familyId);
                    return Mono.empty();
                },
                cacheWriter(cachedSnapshot));

        TokenFamilySnapshot result = reader.findByFamilyId("family-primary").block();

        assertThat(result).isEqualTo(snapshot);
        assertThat(fallbackLookup.get()).isNull();
        assertThat(cachedSnapshot.get()).isNull();
    }

    @Test
    void backfillsRedisWhenFallbackFindsSnapshot() {
        TokenFamilySnapshot snapshot = new TokenFamilySnapshot(
                "family-fallback",
                TokenFamilyStatus.ACTIVE,
                Instant.parse("2030-01-01T00:00:00Z"));
        AtomicReference<TokenFamilySnapshot> cachedSnapshot = new AtomicReference<>();

        TokenFamilyReader reader = new FallbackTokenFamilyReader(
                familyId -> Mono.empty(),
                familyId -> Mono.just(snapshot),
                cacheWriter(cachedSnapshot));

        TokenFamilySnapshot result = reader.findByFamilyId("family-fallback").block();

        assertThat(result).isEqualTo(snapshot);
        assertThat(cachedSnapshot.get()).isEqualTo(snapshot);
    }

    @Test
    void returnsEmptyWhenFallbackCannotFindSnapshot() {
        AtomicReference<TokenFamilySnapshot> cachedSnapshot = new AtomicReference<>();

        TokenFamilyReader reader = new FallbackTokenFamilyReader(
                familyId -> Mono.empty(),
                familyId -> Mono.empty(),
                cacheWriter(cachedSnapshot));

        TokenFamilySnapshot result = reader.findByFamilyId("missing-family").block();

        assertThat(result).isNull();
        assertThat(cachedSnapshot.get()).isNull();
    }

    private RedisTokenFamilyCacheWriter cacheWriter(AtomicReference<TokenFamilySnapshot> cachedSnapshot) {
        return new RedisTokenFamilyCacheWriter(null, "auth:family:") {
            @Override
            public Mono<Void> save(TokenFamilySnapshot snapshot) {
                cachedSnapshot.set(snapshot);
                return Mono.empty();
            }
        };
    }
}
