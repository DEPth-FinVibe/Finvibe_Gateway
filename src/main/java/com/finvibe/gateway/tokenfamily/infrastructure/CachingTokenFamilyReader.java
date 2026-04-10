package com.finvibe.gateway.tokenfamily.infrastructure;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.finvibe.gateway.tokenfamily.application.TokenFamilyReader;
import com.finvibe.gateway.tokenfamily.domain.TokenFamilySnapshot;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * TokenFamily 조회 결과에 짧은 메모리 캐시를 적용하는 infrastructure decorator다.
 */
@RequiredArgsConstructor
public class CachingTokenFamilyReader implements TokenFamilyReader {

    private final TokenFamilyReader delegate;
    private final Duration cacheTtl;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * 캐시를 먼저 확인하고, 없으면 delegate reader에서 조회한다.
     *
     * @param familyId TokenFamily 식별자
     * @return 조회된 TokenFamily 스냅샷, 없으면 empty
     */
    @Override
    public Mono<TokenFamilySnapshot> findByFamilyId(String familyId) {
        CacheEntry cachedEntry = cache.get(familyId);
        if (cachedEntry != null && !cachedEntry.isExpired()) {
            return cachedEntry.snapshot() == null ? Mono.empty() : Mono.just(cachedEntry.snapshot());
        }

        return delegate.findByFamilyId(familyId)
                .doOnNext(snapshot -> cache(familyId, snapshot))
                .switchIfEmpty(Mono.defer(() -> {
                    cache(familyId, null);
                    return Mono.empty();
                }));
    }

    /**
     * 조회 결과를 TTL과 함께 메모리 캐시에 저장한다.
     *
     * @param familyId TokenFamily 식별자
     * @param snapshot 저장할 스냅샷
     */
    private void cache(String familyId, TokenFamilySnapshot snapshot) {
        if (cacheTtl.isZero() || cacheTtl.isNegative()) {
            cache.remove(familyId);
            return;
        }

        cache.put(familyId, new CacheEntry(snapshot, Instant.now().plus(cacheTtl)));
    }

    private record CacheEntry(TokenFamilySnapshot snapshot, Instant expiresAt) {

        private boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
