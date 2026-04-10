package com.finvibe.gateway.tokenfamily.infrastructure;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.redis.core.ReactiveHashOperations;

import com.finvibe.gateway.tokenfamily.application.TokenFamilyReader;
import com.finvibe.gateway.tokenfamily.domain.TokenFamilySnapshot;
import com.finvibe.gateway.tokenfamily.domain.TokenFamilyStatus;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Redis hash에서 TokenFamily 상태를 읽어오는 infrastructure reader다.
 */
@RequiredArgsConstructor
public class RedisTokenFamilyReader implements TokenFamilyReader {

    private static final String STATUS_FIELD = "status";
    private static final String EXPIRES_AT_FIELD = "expiresAt";

    private final ReactiveHashOperations<String, String, String> hashOperations;
    private final String keyPrefix;

    /**
     * Redis의 `status`, `expiresAt` 필드를 읽어 TokenFamily 스냅샷으로 변환한다.
     *
     * @param familyId TokenFamily 식별자
     * @return 조회된 TokenFamily 스냅샷, 없으면 empty
     */
    @Override
    public Mono<TokenFamilySnapshot> findByFamilyId(String familyId) {
        return hashOperations.entries(keyPrefix + familyId)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .flatMap(values -> {
                    if (values.isEmpty()) {
                        return Mono.empty();
                    }

                    String rawStatus = values.get(STATUS_FIELD);
                    String rawExpiresAt = values.get(EXPIRES_AT_FIELD);
                    if (rawStatus == null || rawExpiresAt == null) {
                        return Mono.error(new IllegalStateException("Token family entry is missing required fields"));
                    }

                    return Mono.just(new TokenFamilySnapshot(
                            familyId,
                            TokenFamilyStatus.valueOf(rawStatus),
                            Instant.parse(rawExpiresAt)));
                });
    }
}
