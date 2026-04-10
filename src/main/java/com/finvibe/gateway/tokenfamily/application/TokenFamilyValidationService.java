package com.finvibe.gateway.tokenfamily.application;

import com.finvibe.gateway.tokenfamily.domain.TokenFamilyPolicy;
import com.finvibe.gateway.tokenfamily.domain.TokenFamilyValidationResult;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * TokenFamily 조회와 도메인 정책 평가를 조합하는 application service다.
 */
@RequiredArgsConstructor
public class TokenFamilyValidationService {

    private final TokenFamilyReader tokenFamilyReader;
    private final TokenFamilyPolicy tokenFamilyPolicy;

    /**
     * TokenFamily 상태를 검증해 Gateway가 사용할 최종 판정 결과를 반환한다.
     *
     * @param familyId TokenFamily 식별자
     * @return 검증 결과
     */
    public Mono<TokenFamilyValidationResult> validate(String familyId) {
        return tokenFamilyReader.findByFamilyId(familyId)
                .map(tokenFamilyPolicy::validate)
                .switchIfEmpty(Mono.just(TokenFamilyValidationResult.DENY_SERVICE_UNAVAILABLE))
                .onErrorReturn(TokenFamilyValidationResult.DENY_SERVICE_UNAVAILABLE);
    }
}
