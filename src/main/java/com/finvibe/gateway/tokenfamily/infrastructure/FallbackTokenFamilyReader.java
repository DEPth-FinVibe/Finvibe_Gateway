package com.finvibe.gateway.tokenfamily.infrastructure;

import com.finvibe.gateway.tokenfamily.application.TokenFamilyReader;
import com.finvibe.gateway.tokenfamily.domain.TokenFamilySnapshot;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class FallbackTokenFamilyReader implements TokenFamilyReader {

    private final TokenFamilyReader primaryReader;
    private final TokenFamilyReader fallbackReader;
    private final RedisTokenFamilyCacheWriter cacheWriter;

    @Override
    public Mono<TokenFamilySnapshot> findByFamilyId(String familyId) {
        return primaryReader.findByFamilyId(familyId)
                .switchIfEmpty(Mono.defer(() -> fallbackReader.findByFamilyId(familyId)
                        .flatMap(snapshot -> cacheWriter.save(snapshot)
                                .onErrorResume(ignored -> Mono.empty())
                                .thenReturn(snapshot))));
    }
}
