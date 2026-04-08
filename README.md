# Finvibe Gateway

기본 Spring Cloud Gateway Server WebMVC 스캐폴드입니다.

## 포함된 예시

- `/api/auth/**` -> `http://localhost:8081/**`
- `/api/core/**` -> `http://localhost:8082/**`
- `/external/httpbin/**` -> `https://httpbin.org/**`
- `/gateway/info` -> 현재 게이트웨이 예시 라우트 정보 확인

## 실행

```bash
./gradlew bootRun
```

## 빠른 확인

```bash
curl http://localhost:8080/gateway/info
curl http://localhost:8080/external/httpbin/get
```

## 라우트 커스터마이징

`src/main/resources/application.yml`에서 백엔드 URL을 바꿀 수 있습니다.

```yaml
finvibe:
  gateway:
    services:
      auth-url: http://localhost:9001
      core-url: http://localhost:9002
```
