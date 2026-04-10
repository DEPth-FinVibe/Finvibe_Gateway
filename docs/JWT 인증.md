# JWT 인증 가이드

Finvibe Gateway는 Spring Cloud Gateway(WebFlux)와 Spring Security OAuth2 Resource Server를 사용해 JWT를 검증한다.

## 적용 범위

- `/api/**` : JWT 인증 필요
- `/api/auth/**` : 공개 경로
- `/gateway/**` : 공개 경로
- `/actuator/health`, `/actuator/info` : 공개 경로
- `/api/market/ws/**` : 현재 `/api/**`에 포함되므로 JWT 인증 대상

실제 경로 정책은 `src/main/java/com/finvibe/gateway/config/GatewaySecurityConfig.java` 기준이다.

## 동작 방식

1. 클라이언트가 `Authorization: Bearer <JWT>` 헤더로 요청한다.
2. Gateway가 JWT 서명과 만료 시간을 검증한다.
3. 보호 경로(`/api/**`, 단 `/api/auth/**` 제외)는 JWT의 `token_family_id` claim을 읽어 Redis에서 TokenFamily 상태를 확인한다.
4. family 상태가 `ACTIVE`면 요청을 downstream 서비스로 전달한다.
5. JWT 또는 family 검증에 실패하면 Gateway에서 바로 `401 Unauthorized`를 반환한다.
6. Redis 조회가 실패하면 Gateway에서 `503 Service Unavailable`를 반환한다.

Gateway는 인증과 TokenFamily 유효성의 1차 검증을 수행한다. 세부 권한 체크가 필요하면 downstream WAS에서 추가 인가를 수행할 수 있다.

## 설정 방법

JWT 검증 설정은 `src/main/resources/application.yml`에 들어 있다.

```yaml
spring:
  data:
    redis:
      url: ${FINVIBE_GATEWAY_TOKEN_FAMILY_REDIS_URI:redis://127.0.0.1:6379/0}
      timeout: ${FINVIBE_GATEWAY_TOKEN_FAMILY_COMMAND_TIMEOUT:PT3S}
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${FINVIBE_GATEWAY_JWT_ISSUER_URI:}
          jwk-set-uri: ${FINVIBE_GATEWAY_JWT_JWK_SET_URI:}

finvibe:
  gateway:
    security:
      jwt:
        secret: ${FINVIBE_GATEWAY_JWT_SECRET:finvibe-gateway-dev-secret-change-me-1234567890}
    token-family:
      enabled: ${FINVIBE_GATEWAY_TOKEN_FAMILY_ENABLED:true}
      redis-uri: ${FINVIBE_GATEWAY_TOKEN_FAMILY_REDIS_URI:redis://127.0.0.1:6379/0}
      key-prefix: ${FINVIBE_GATEWAY_TOKEN_FAMILY_KEY_PREFIX:token-family:}
      cache-ttl: ${FINVIBE_GATEWAY_TOKEN_FAMILY_CACHE_TTL:PT30S}
      connect-timeout: ${FINVIBE_GATEWAY_TOKEN_FAMILY_CONNECT_TIMEOUT:PT2S}
      command-timeout: ${FINVIBE_GATEWAY_TOKEN_FAMILY_COMMAND_TIMEOUT:PT3S}
```

### 1. HMAC secret 기반 검증

개발 환경이나 내부 서비스 간 통신처럼 대칭키 기반 JWT를 쓸 경우 `FINVIBE_GATEWAY_JWT_SECRET`만 설정하면 된다.

```bash
export FINVIBE_GATEWAY_JWT_SECRET='replace-with-long-random-secret'
./gradlew bootRun
```

- 현재 구현은 HS256 기준이다.
- secret은 충분히 긴 랜덤 문자열을 사용해야 한다.
- 기본값은 로컬 개발용이므로 운영 환경에서는 반드시 교체해야 한다.

### 2. Issuer 또는 JWK Set 기반 검증

외부 인증 서버가 JWT를 발급하고 공개키 기반으로 검증해야 하면 아래 환경변수 중 하나를 사용한다.

```bash
export FINVIBE_GATEWAY_JWT_ISSUER_URI='https://issuer.example.com'
```

또는

```bash
export FINVIBE_GATEWAY_JWT_JWK_SET_URI='https://issuer.example.com/.well-known/jwks.json'
```

- `issuer-uri` 또는 `jwk-set-uri`를 사용할 때는 Spring이 제공하는 디코더가 우선 사용된다.
- 이 경우 `FINVIBE_GATEWAY_JWT_SECRET`는 fallback 용도로만 남는다.

## TokenFamily 검증 계약

- AccessToken은 `token_family_id` claim을 반드시 포함해야 한다.
- RefreshToken family 검사는 Gateway가 아니라 인증 서비스의 `/api/auth/refresh`에서 직접 수행한다.
- Gateway는 Redis key `token-family:{familyId}`를 기본값으로 사용한다. prefix는 `finvibe.gateway.token-family.key-prefix`로 조정할 수 있다.
- Redis value는 hash 구조를 사용하며 최소 필드는 아래와 같다.

```text
status=ACTIVE|INVALIDATED|EXPIRED
expiresAt=2030-01-01T00:00:00Z
```

- `status`는 `ACTIVE`, `INVALIDATED`, `EXPIRED` 중 하나여야 한다.
- `expiresAt`은 ISO-8601 UTC 시각 문자열이어야 한다.
- Redis 값이 `ACTIVE`여도 `expiresAt`이 현재 시각 이전이면 Gateway는 `EXPIRED`로 간주해 차단한다.
- Gateway는 조회 결과를 짧게 캐시하며, 기본 TTL은 30초다.

## 요청 예시

보호 경로 호출 예시:

```bash
curl http://localhost:8080/api/secured \
  -H "Authorization: Bearer <jwt>"
```

공개 경로 호출 예시:

```bash
curl http://localhost:8080/gateway/info
curl http://localhost:8080/api/auth/login
```

## 테스트 범위

다음 시나리오를 통합 테스트로 검증한다.

- JWT 없이 `/api/**` 호출 시 `401`
- 공개 경로(`/gateway/info`)는 JWT 없이 `200`
- 유효한 JWT + `ACTIVE` family로 `/api/**` 호출 시 downstream 전달 성공
- 잘못된 서명의 JWT로 `/api/**` 호출 시 `401`
- `token_family_id` claim 없는 JWT로 `/api/**` 호출 시 `401`
- `INVALIDATED` family 또는 과거 `expiresAt` family로 `/api/**` 호출 시 `401`
- Redis 조회 실패 시 `/api/**` 호출은 `503`

테스트 코드는 `src/test/java/com/finvibe/gateway/GatewaySecurityIntegrationTests.java`에 있다.

## 운영 시 주의사항

- 운영 환경에서는 기본 secret을 절대 그대로 사용하지 않는다.
- 가능하면 운영은 `issuer-uri` 또는 `jwk-set-uri` 기반 공개키 검증으로 맞춘다.
- Swagger, 정적 파일, 로그인 API 등 공개해야 하는 경로가 늘어나면 `GatewaySecurityConfig`의 공개 경로 목록을 함께 갱신한다.
- Redis는 인증 서비스와 Gateway가 함께 사용하는 내부 저장소여야 하며, key schema 변경은 계약 변경으로 다뤄야 한다.
