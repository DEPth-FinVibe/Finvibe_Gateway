package com.finvibe.gateway.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.reactive.function.client.WebClient;

import com.finvibe.gateway.tokenfamily.adapter.TokenFamilyValidationWebFilter;
import com.finvibe.gateway.tokenfamily.application.TokenFamilyReader;
import com.finvibe.gateway.tokenfamily.application.TokenFamilyValidationService;
import com.finvibe.gateway.tokenfamily.domain.TokenFamilyPolicy;
import com.finvibe.gateway.tokenfamily.infrastructure.CachingTokenFamilyReader;
import com.finvibe.gateway.tokenfamily.infrastructure.FallbackTokenFamilyReader;
import com.finvibe.gateway.tokenfamily.infrastructure.RedisTokenFamilyCacheWriter;
import com.finvibe.gateway.tokenfamily.infrastructure.RedisTokenFamilyReader;
import com.finvibe.gateway.tokenfamily.infrastructure.WasTokenFamilyReader;

/**
 * Gateway 보안 체인과 TokenFamily 관련 bean 구성을 담당한다.
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(TokenFamilyValidationProperties.class)
public class GatewaySecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/gateway/**",
            "/auth/**",
            "/market/ws/**",
            "/actuator/health",
            "/actuator/info"
    };

    /**
     * JWT 인증과 TokenFamily 검증 필터를 포함한 보안 체인을 구성한다.
     *
     * @param http security builder
     * @param tokenFamilyValidationProperties TokenFamily 검증 설정
     * @param tokenFamilyValidationWebFilterProvider TokenFamily 검증 필터 provider
     * @return security filter chain
     */
    @Bean
    SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            TokenFamilyValidationProperties tokenFamilyValidationProperties,
            ObjectProvider<TokenFamilyValidationWebFilter> tokenFamilyValidationWebFilterProvider) {
        if (tokenFamilyValidationProperties.isEnabled()) {
            tokenFamilyValidationWebFilterProvider.ifAvailable(
                    filter -> http.addFilterAfter(filter, SecurityWebFiltersOrder.AUTHENTICATION));
        }

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        .pathMatchers("/internal/**").denyAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    /**
     * WebFlux 요청에서 TokenFamily 검증을 수행하는 adapter 필터를 생성한다.
     *
     * @param tokenFamilyValidationService TokenFamily 검증 유스케이스
     * @return TokenFamily 검증 필터
     */
    @Bean
    @ConditionalOnProperty(prefix = "finvibe.gateway.token-family", name = "enabled", havingValue = "true", matchIfMissing = true)
    TokenFamilyValidationWebFilter tokenFamilyValidationWebFilter(TokenFamilyValidationService tokenFamilyValidationService) {
        return new TokenFamilyValidationWebFilter(tokenFamilyValidationService);
    }

    /**
     * TokenFamily 조회와 도메인 정책을 조합해 최종 검증 결과를 반환하는 application service를 생성한다.
     *
     * @param tokenFamilyReader TokenFamily 조회 포트
     * @param tokenFamilyPolicy TokenFamily 도메인 정책
     * @return TokenFamily 검증 서비스
     */
    @Bean
    @ConditionalOnProperty(prefix = "finvibe.gateway.token-family", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    TokenFamilyValidationService tokenFamilyValidationService(
            @Qualifier("tokenFamilyReader") TokenFamilyReader tokenFamilyReader,
            TokenFamilyPolicy tokenFamilyPolicy) {
        return new TokenFamilyValidationService(tokenFamilyReader, tokenFamilyPolicy);
    }

    /**
     * TokenFamily 상태를 판정하는 도메인 정책을 생성한다.
     *
     * @param clock 현재 시각 기준 clock
     * @return TokenFamily 정책
     */
    @Bean
    @ConditionalOnProperty(prefix = "finvibe.gateway.token-family", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    TokenFamilyPolicy tokenFamilyPolicy(Clock clock) {
        return new TokenFamilyPolicy(clock);
    }

    /**
     * 만료 판정에 사용할 시스템 clock을 제공한다.
     *
     * @return UTC 기준 시스템 clock
     */
    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Redis hash에서 TokenFamily 정보를 읽는 infrastructure reader를 생성한다.
     *
     * @param properties TokenFamily 설정
     * @param reactiveStringRedisTemplate reactive Redis template
     * @return Redis 기반 reader
     */
    @Bean
    @ConditionalOnProperty(prefix = "finvibe.gateway.token-family", name = "enabled", havingValue = "true", matchIfMissing = true)
    RedisTokenFamilyReader redisTokenFamilyReader(
            TokenFamilyValidationProperties properties,
            ReactiveStringRedisTemplate reactiveStringRedisTemplate) {
        return new RedisTokenFamilyReader(
                reactiveStringRedisTemplate.opsForHash(),
                properties.getKeyPrefix());
    }

    @Bean
    @ConditionalOnProperty(prefix = "finvibe.gateway.token-family", name = "enabled", havingValue = "true", matchIfMissing = true)
    RedisTokenFamilyCacheWriter redisTokenFamilyCacheWriter(
            TokenFamilyValidationProperties properties,
            ReactiveStringRedisTemplate reactiveStringRedisTemplate) {
        return new RedisTokenFamilyCacheWriter(reactiveStringRedisTemplate, properties.getKeyPrefix());
    }

    @Bean
    @ConditionalOnProperty(prefix = "finvibe.gateway.token-family", name = "enabled", havingValue = "true", matchIfMissing = true)
    WasTokenFamilyReader wasTokenFamilyReader(
            @Value("${finvibe.gateway.services.was-url}") String wasServiceUrl) {
        return new WasTokenFamilyReader(WebClient.builder()
                .baseUrl(wasServiceUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024))
                .build());
    }

    /**
     * Redis reader 위에 짧은 TTL 캐시를 덧붙인 TokenFamily 조회 포트를 생성한다.
     *
     * @param properties TokenFamily 설정
     * @param redisTokenFamilyReader Redis 기반 reader
     * @return 캐시가 적용된 TokenFamily reader
     */
    @Bean
    @ConditionalOnProperty(prefix = "finvibe.gateway.token-family", name = "enabled", havingValue = "true", matchIfMissing = true)
    TokenFamilyReader tokenFamilyReader(
            TokenFamilyValidationProperties properties,
            RedisTokenFamilyReader redisTokenFamilyReader,
            WasTokenFamilyReader wasTokenFamilyReader,
            RedisTokenFamilyCacheWriter redisTokenFamilyCacheWriter) {
        TokenFamilyReader fallbackTokenFamilyReader = new FallbackTokenFamilyReader(
                redisTokenFamilyReader,
                wasTokenFamilyReader,
                redisTokenFamilyCacheWriter);
        return new CachingTokenFamilyReader(fallbackTokenFamilyReader, properties.getCacheTtl());
    }

    /**
     * 대칭키 기반 JWT 디코더를 생성한다.
     *
     * @param secret HMAC secret
     * @return reactive JWT decoder
     */
    @Bean
    @ConditionalOnMissingBean(ReactiveJwtDecoder.class)
    @ConditionalOnProperty(prefix = "finvibe.gateway.security.jwt", name = "secret")
    ReactiveJwtDecoder reactiveJwtDecoder(
            @Value("${finvibe.gateway.security.jwt.secret}") String secret) {
        SecretKey secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
