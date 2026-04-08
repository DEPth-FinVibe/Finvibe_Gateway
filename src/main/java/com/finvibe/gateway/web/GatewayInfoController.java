package com.finvibe.gateway.web;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/gateway")
public class GatewayInfoController {

    private final String authServiceUrl;
    private final String coreServiceUrl;

    public GatewayInfoController(
            @Value("${finvibe.gateway.services.auth-url}") String authServiceUrl,
            @Value("${finvibe.gateway.services.core-url}") String coreServiceUrl) {
        this.authServiceUrl = authServiceUrl;
        this.coreServiceUrl = coreServiceUrl;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "application", "finvibe-gateway",
                "routes", List.of(
                        Map.of("id", "auth-service", "path", "/api/auth/**", "target", authServiceUrl),
                        Map.of("id", "core-service", "path", "/api/core/**", "target", coreServiceUrl),
                        Map.of("id", "httpbin-sample", "path", "/external/httpbin/**", "target", "https://httpbin.org")));
    }
}
