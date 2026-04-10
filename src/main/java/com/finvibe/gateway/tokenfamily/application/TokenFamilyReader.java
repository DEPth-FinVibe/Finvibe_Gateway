package com.finvibe.gateway.tokenfamily.application;

import com.finvibe.gateway.tokenfamily.domain.TokenFamilySnapshot;

import reactor.core.publisher.Mono;

/**
 * TokenFamily 정보를 로드하는 application 포트다.
 */
public interface TokenFamilyReader {

    /**
     * familyId로 TokenFamily 스냅샷을 조회한다.
     *
     * @param familyId TokenFamily 식별자
     * @return 조회된 TokenFamily 스냅샷, 없으면 empty
     */
    Mono<TokenFamilySnapshot> findByFamilyId(String familyId);
}
