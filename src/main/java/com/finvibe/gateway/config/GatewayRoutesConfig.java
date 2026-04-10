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
     * 시장 WebSocket과 일반 WAS 요청에 대한 라우트를 구성한다.
     *
     * @param builder route locator builder
     * @param marketServiceUrl 시장 WebSocket 대상 주소
     * @param wasServiceUrl 일반 API 대상 주소
     * @return 구성된 route locator
     */
    @Bean
    RouteLocator gatewayRoutes(
            RouteLocatorBuilder builder,
            @Value("${finvibe.gateway.services.market-url}") String marketServiceUrl,
            @Value("${finvibe.gateway.services.was-url}") String wasServiceUrl) {
        return builder.routes()
                .route("market-ws-service", r -> r.order(-1)
                        .path("/api/market/ws/**")
                        .filters(f -> f.stripPrefix(1))
                        .uri(marketServiceUrl))
                .route("was-service", r -> r.order(0)
                        .path("/api/**")
                        .uri(wasServiceUrl))
                .build();
    }
}
