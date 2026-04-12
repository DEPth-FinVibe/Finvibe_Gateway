package com.finvibe.gateway.tokenfamily.infrastructure;

import java.time.Instant;

import org.springframework.web.reactive.function.client.WebClient;

import com.finvibe.gateway.tokenfamily.application.TokenFamilyReader;
import com.finvibe.gateway.tokenfamily.domain.TokenFamilySnapshot;
import com.finvibe.gateway.tokenfamily.domain.TokenFamilyStatus;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class WasTokenFamilyReader implements TokenFamilyReader {

    private static final String TOKEN_FAMILY_PATH = "/internal/auth/token-families/{familyId}";

    private final WebClient webClient;

    @Override
    public Mono<TokenFamilySnapshot> findByFamilyId(String familyId) {
        return webClient.get()
                .uri(TOKEN_FAMILY_PATH, familyId)
                .exchangeToMono(response -> {
                    if (response.statusCode().value() == 404) {
                        return Mono.empty();
                    }
                    if (response.statusCode().isError()) {
                        return response.createException().flatMap(Mono::error);
                    }
                    return response.bodyToMono(TokenFamilySnapshotResponse.class)
                            .map(payload -> new TokenFamilySnapshot(
                                    payload.familyId(),
                                    TokenFamilyStatus.valueOf(payload.status()),
                                    Instant.parse(payload.expiresAt())));
                });
    }

    private record TokenFamilySnapshotResponse(
            String familyId,
            String status,
            String expiresAt) {
    }
}
