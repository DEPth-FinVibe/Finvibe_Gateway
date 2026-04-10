package com.finvibe.gateway.tokenfamily.domain;

import java.time.Instant;

/**
 * TokenFamily의 현재 검증에 필요한 최소 상태 스냅샷이다.
 *
 * @param familyId TokenFamily 식별자
 * @param status 현재 저장된 상태
 * @param expiresAt Sliding expiration 기준 만료 시각
 */
public record TokenFamilySnapshot(
        String familyId,
        TokenFamilyStatus status,
        Instant expiresAt) {
}
