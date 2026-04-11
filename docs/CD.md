# Finvibe Gateway CD 가이드

이 문서는 [`CD 워크플로`](/Users/cmh/Documents/Finvibe/Finvibe_Gateway/.github/workflows/cd.yml) 기준으로 Finvibe Gateway의 배포 파이프라인 구성을 정리한다.

## 개요

`main` 브랜치에 push 되면 GitHub Actions가 다음 순서로 동작한다.

1. 저장소를 체크아웃한다.
2. Docker 이미지를 빌드한다.
3. Docker Hub에 이미지를 push 한다.
4. 배포 서버에 SSH로 접속한다.
5. 기존 컨테이너를 교체 실행한다.
6. `/actuator/health/readiness`로 readiness 확인 후 성공 시 배포를 종료한다.

## 워크플로 파일

- [`.github/workflows/cd.yml`](/Users/cmh/Documents/Finvibe/Finvibe_Gateway/.github/workflows/cd.yml)

## GitHub Secrets

### 공통 인프라 Secrets

기존 파이프라인과 동일하게 필요한 값이다.

- `DOCKERHUB_USERNAME`
- `DOCKERHUB_TOKEN`
- `SSH_HOST`
- `SSH_USER`
- `SSH_PRIVATE_KEY`
- `SSH_PORT`

### Gateway 전용 Secrets

기존 예시 파이프라인과 비교했을 때 새로 필요한 값이다.

- `JWT_SECRET`
- `FINVIBE_GATEWAY_TOKEN_FAMILY_REDIS_URI`
- `FINVIBE_GATEWAY_WEBSOCKET_LISTENER_URL`
- `FINVIBE_GATEWAY_WAS_URL`

## GitHub Variables

없어도 기본값으로 동작하지만, 환경에 맞게 재정의할 수 있다.

- `DOCKER_IMAGE_NAME`
  기본값: `${DOCKERHUB_USERNAME}/finvibe-gateway`
- `DEPLOY_CONTAINER_NAME`
  기본값: `finvibe-gateway`
- `APP_PORT`
  기본값: `8070`
- `LOG_DIR_HOST`
  기본값: `/var/log/finvibe-gateway`
- `PRIMARY_DOCKER_NETWORK`
  기본값: `infra_bridge`
- `SECONDARY_DOCKER_NETWORK`
  기본값: `monitoring_net`
- `SPRING_PROFILES_ACTIVE`
  기본값: `prod`

## 서버 선행조건

배포 대상 서버에는 아래 조건이 먼저 준비되어 있어야 한다.

- Docker가 설치되어 있어야 한다.
- `PRIMARY_DOCKER_NETWORK` 값에 해당하는 Docker network가 존재해야 한다.
- `SECONDARY_DOCKER_NETWORK` 값에 해당하는 Docker network가 존재해야 한다.
- GitHub Actions에서 전달한 SSH 계정이 Docker 명령을 실행할 수 있어야 한다.
- `APP_PORT` 포트가 외부에서 접근 가능해야 한다.

네트워크가 없으면 워크플로는 아래 단계에서 실패한다.

- `docker network inspect "${PRIMARY_DOCKER_NETWORK}"`
- `docker network inspect "${SECONDARY_DOCKER_NETWORK}"`

## 애플리케이션/이미지 구성

CD 파이프라인이 동작하도록 아래 파일이 함께 구성되어 있다.

- [`Dockerfile`](/Users/cmh/Documents/Finvibe/Finvibe_Gateway/Dockerfile)
- [`.dockerignore`](/Users/cmh/Documents/Finvibe/Finvibe_Gateway/.dockerignore)
- [`build.gradle`](/Users/cmh/Documents/Finvibe/Finvibe_Gateway/build.gradle)
- [`src/main/resources/application.yml`](/Users/cmh/Documents/Finvibe/Finvibe_Gateway/src/main/resources/application.yml)

주요 반영 사항은 다음과 같다.

- Spring Boot Actuator 추가
- readiness probe 활성화
- `bootJar` 기반 컨테이너 이미지 빌드

## 배포 시 주입되는 환경변수

배포 컨테이너에는 아래 값들이 전달된다.

- `SPRING_PROFILES_ACTIVE`
- `LOG_JSON_PATH`
- `FINVIBE_GATEWAY_JWT_SECRET`
  GitHub Secret `JWT_SECRET` 값이 이 환경변수로 전달된다.
- `FINVIBE_GATEWAY_TOKEN_FAMILY_REDIS_URI`
- `FINVIBE_GATEWAY_WEBSOCKET_LISTENER_URL`
- `FINVIBE_GATEWAY_WAS_URL`

## 헬스체크

배포 완료 판정은 아래 엔드포인트를 기준으로 한다.

```text
http://localhost:${APP_PORT}/actuator/health/readiness
```

워크플로는 최대 60회, 5초 간격으로 readiness를 확인한다.

## 운영 절차

1. GitHub Secrets를 등록한다.
2. 필요하면 GitHub Variables를 등록한다.
3. 배포 서버에 Docker network를 준비한다.
4. `main` 브랜치에 push 한다.
5. Actions 탭에서 `CD` 워크플로 실행 결과를 확인한다.

## 확인 명령 예시

서버에서 직접 확인할 때는 아래 명령을 사용할 수 있다.

```bash
docker ps
docker logs finvibe-gateway
curl http://localhost:8070/actuator/health/readiness
```

포트 또는 컨테이너 이름을 변경했다면 값도 함께 바꿔서 확인한다.
