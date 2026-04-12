package com.finvibe.gateway.tokenfamily.infrastructure;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import com.finvibe.gateway.tokenfamily.domain.TokenFamilySnapshot;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class RedisTokenFamilyCacheWriter {

    private static final String STATUS_FIELD = "status";
    private static final String EXPIRES_AT_FIELD = "expiresAt";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public Mono<Void> save(TokenFamilySnapshot snapshot) {
        String key = keyPrefix + snapshot.familyId();
        return redisTemplate.opsForHash().putAll(key, Map.of(
                        STATUS_FIELD, snapshot.status().name(),
                        EXPIRES_AT_FIELD, snapshot.expiresAt().toString()))
                .then(redisTemplate.expire(key, resolveTtl(snapshot.expiresAt())).then())
                .then();
    }

    private Duration resolveTtl(Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt.plus(Duration.ofDays(1)));
        if (ttl.isNegative() || ttl.isZero()) {
            return Duration.ofHours(1);
        }
        return ttl;
    }
}
