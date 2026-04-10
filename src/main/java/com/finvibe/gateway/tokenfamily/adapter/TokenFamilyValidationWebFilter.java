package com.finvibe.gateway.tokenfamily.adapter;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.finvibe.gateway.tokenfamily.application.TokenFamilyValidationService;
import com.finvibe.gateway.tokenfamily.domain.TokenFamilyValidationResult;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * WebFlux 보안 체인에서 TokenFamily 검증 유스케이스를 호출하는 adapter 필터다.
 */
@RequiredArgsConstructor
public class TokenFamilyValidationWebFilter implements WebFilter {

    static final String TOKEN_FAMILY_ID_CLAIM = "token_family_id";

    private final TokenFamilyValidationService tokenFamilyValidationService;

    /**
     * 보호 경로 요청에 대해 TokenFamily 검증 결과를 HTTP 응답으로 매핑한다.
     *
     * @param exchange 현재 요청/응답 exchange
     * @param chain 다음 필터 체인
     * @return 필터 처리 결과
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!requiresValidation(exchange)) {
            return chain.filter(exchange);
        }

        return exchange.getPrincipal()
                .cast(Authentication.class)
                .flatMap(authentication -> {
                    if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
                        return chain.filter(exchange).thenReturn(Boolean.TRUE);
                    }

                    String familyId = jwtAuthenticationToken.getToken().getClaimAsString(TOKEN_FAMILY_ID_CLAIM);
                    if (familyId == null || familyId.isBlank()) {
                        return writeError(exchange, HttpStatus.UNAUTHORIZED).thenReturn(Boolean.TRUE);
                    }

                    return tokenFamilyValidationService.validate(familyId)
                            .flatMap(result -> switch (result) {
                                case ALLOW -> chain.filter(exchange).thenReturn(Boolean.TRUE);
                                case DENY_UNAUTHORIZED -> writeError(exchange, HttpStatus.UNAUTHORIZED).thenReturn(Boolean.TRUE);
                                case DENY_SERVICE_UNAVAILABLE -> writeError(exchange, HttpStatus.SERVICE_UNAVAILABLE)
                                        .thenReturn(Boolean.TRUE);
                            });
                })
                .switchIfEmpty(Mono.defer(() -> chain.filter(exchange).thenReturn(Boolean.TRUE)))
                .then();
    }

    /**
     * TokenFamily 검증이 필요한 요청 경로인지 판단한다.
     *
     * @param exchange 현재 exchange
     * @return 검증 대상 여부
     */
    private boolean requiresValidation(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        return path.startsWith("/api/")
                && !path.startsWith("/api/auth/")
                && !"/api/auth".equals(path);
    }

    /**
     * 검증 실패 결과를 지정한 HTTP 상태 코드로 응답한다.
     *
     * @param exchange 현재 exchange
     * @param status 반환할 상태 코드
     * @return 완료 신호
     */
    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status) {
        return Mono.defer(() -> {
            exchange.getResponse().setStatusCode(status);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return exchange.getResponse().setComplete();
        });
    }
}
