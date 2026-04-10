package com.finvibe.gateway.tokenfamily.domain;

import java.time.Clock;
import java.time.Instant;

import lombok.RequiredArgsConstructor;

/**
 * TokenFamily 상태와 만료 시각을 바탕으로 인증 허용 여부를 판단하는 도메인 정책이다.
 */
@RequiredArgsConstructor
public class TokenFamilyPolicy {

    private final Clock clock;

    /**
     * TokenFamily 스냅샷을 검증해 허용/차단 결과를 반환한다.
     *
     * @param snapshot TokenFamily 현재 상태
     * @return 검증 결과
     */
    public TokenFamilyValidationResult validate(TokenFamilySnapshot snapshot) {
        TokenFamilyStatus effectiveStatus = resolveEffectiveStatus(snapshot);
        return switch (effectiveStatus) {
            case ACTIVE -> TokenFamilyValidationResult.ALLOW;
            case INVALIDATED, EXPIRED -> TokenFamilyValidationResult.DENY_UNAUTHORIZED;
        };
    }

    /**
     * 저장된 상태와 만료 시각을 조합해 현재 시점의 유효 상태를 계산한다.
     *
     * @param snapshot TokenFamily 현재 상태
     * @return 현재 시점 기준 유효 상태
     */
    private TokenFamilyStatus resolveEffectiveStatus(TokenFamilySnapshot snapshot) {
        Instant now = Instant.now(clock);
        if (snapshot.expiresAt() != null && !snapshot.expiresAt().isAfter(now)) {
            return TokenFamilyStatus.EXPIRED;
        }
        return snapshot.status();
    }
}
