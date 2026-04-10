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

    private final String marketServiceUrl;
    private final String wasServiceUrl;

    public GatewayInfoController(
            @Value("${finvibe.gateway.services.market-url}") String marketServiceUrl,
            @Value("${finvibe.gateway.services.was-url}") String wasServiceUrl) {
        this.marketServiceUrl = marketServiceUrl;
        this.wasServiceUrl = wasServiceUrl;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "application", "finvibe-gateway",
                "routes", List.of(
                        Map.of("id", "market-ws-service", "path", "/api/market/ws/**", "target", marketServiceUrl),
                        Map.of("id", "was-service", "path", "/api/**", "target", wasServiceUrl)));
    }
}
