package com.finvibe.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutesConfig {

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
