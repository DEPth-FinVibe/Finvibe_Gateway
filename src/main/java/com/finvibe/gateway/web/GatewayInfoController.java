package com.finvibe.gateway.web;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 현재 Gateway의 기본 메타데이터와 라우팅 정보를 노출하는 컨트롤러다.
 */
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

    /**
     * Gateway 식별자와 핵심 라우팅 정보를 반환한다.
     *
     * @return application 이름과 route 목록
     */
    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "application", "finvibe-gateway",
                "routes", List.of(
                        Map.of("id", "market-ws-service", "path", "/api/market/ws/**", "target", marketServiceUrl),
                        Map.of("id", "was-service", "path", "/api/**", "target", wasServiceUrl)));
    }
}
