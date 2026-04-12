package com.finvibe.gateway.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * TokenFamily 검증에 필요한 Gateway 설정값을 바인딩한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "finvibe.gateway.token-family")
public class TokenFamilyValidationProperties {

    private boolean enabled = true;
    private String redisUri = "redis://127.0.0.1:6379/0";
    private String keyPrefix = "auth:family:";
    private Duration cacheTtl = Duration.ofSeconds(30);
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration commandTimeout = Duration.ofSeconds(3);
}
