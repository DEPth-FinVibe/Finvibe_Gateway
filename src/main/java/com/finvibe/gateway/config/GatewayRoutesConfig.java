package com.finvibe.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway가 downstream 서비스로 요청을 전달하는 기본 라우팅을 정의한다.
 */
@Configuration
public class GatewayRoutesConfig {

    /**
     * WAS가 신뢰하는 인증 주입 헤더. 클라이언트가 직접 보낼 수 없도록 gateway에서 제거한다.
     */
    private static final String[] TRUSTED_IDENTITY_HEADERS = {
            "X-Authenticated-User-Id",
            "X-Authenticated-Role",
            "X-Token-Family-Id"
    };

    /**
     * 시장 WebSocket과 일반 WAS 요청에 대한 라우트를 구성한다.
     *
     * @param builder route locator builder
     * @param websocketListenerUrl 시장 WebSocket 대상 주소
     * @param wasServiceUrl 일반 API 대상 주소
     * @return 구성된 route locator
     */
    @Bean
    RouteLocator gatewayRoutes(
            RouteLocatorBuilder builder,
            @Value("${finvibe.gateway.services.websocket-listener-url}") String websocketListenerUrl,
            @Value("${finvibe.gateway.services.was-url}") String wasServiceUrl) {
        return builder.routes()
                .route("websocketListener", r -> r.order(-1)
                        .path("/market/ws/**")
                        .uri(websocketListenerUrl))
                .route("was-service", r -> r.order(0)
                        .path("/**")
                        .filters(f -> {
                            for (String header : TRUSTED_IDENTITY_HEADERS) {
                                f.removeRequestHeader(header);
                            }
                            return f;
                        })
                        .uri(wasServiceUrl))
                .build();
    }
}
