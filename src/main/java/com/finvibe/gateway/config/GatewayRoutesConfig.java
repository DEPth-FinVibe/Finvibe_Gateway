package com.finvibe.gateway.config;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.rewritePath;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.stripPrefix;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    RouterFunction<ServerResponse> gatewayRoutes(
            @Value("${finvibe.gateway.services.auth-url}") String authServiceUrl,
            @Value("${finvibe.gateway.services.core-url}") String coreServiceUrl) {
        return route("auth-service")
                .route(path("/api/auth/**"), http())
                .before(uri(authServiceUrl))
                .before(stripPrefix(2))
                .build()
            .and(route("core-service")
                .route(path("/api/core/**"), http())
                .before(uri(coreServiceUrl))
                .before(stripPrefix(2))
                .build())
            .and(route("httpbin-sample")
                .route(path("/external/httpbin/**"), http())
                .before(uri("https://httpbin.org"))
                .before(rewritePath("/external/httpbin/?(?<segment>.*)", "/${segment}"))
                .build());
    }
}
