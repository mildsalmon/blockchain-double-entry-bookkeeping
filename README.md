# Blockchain Double-Entry Bookkeeping

Ethereum 트랜잭션을 복식부기 분개로 생성하고, 회계사가 검토/수정/승인/내보내기할 수 있는 감사 워크벤치입니다.

## Prerequisites

- Docker / Docker Compose
- Java 17 (backend)
- Node.js 22+ / npm (frontend)
- [Task](https://taskfile.dev) CLI (`brew install go-task/tap/go-task`)

## Quick Start

```bash
# 1) .env 준비 + 프론트 의존성 설치
task setup

# 2) DB 실행
task up

# 3) 백엔드/프론트 동시 실행
task dev
```

접속:
- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8080`

## Useful Tasks

```bash
task                 # 전체 task 목록
task backend:run     # 백엔드만 실행
task frontend:dev    # 프론트만 실행
task verify          # backend test + frontend build
task db:reset        # DB 볼륨 초기화 (로컬 데이터 삭제)
task down            # DB 종료
task db:logs         # DB 로그 확인
```

## Raw Commands (without Task)

```bash
docker compose up -d
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun
cd frontend && npm install && npm run dev
```

## Troubleshooting

`task dev` 실행 시 아래와 같은 Flyway 오류가 나오면:

`Either revert the changes to the migration, or run repair to update the schema history.`

로컬 DB에 저장된 migration checksum과 현재 코드가 불일치한 상태입니다. 로컬 개발 환경 기준 가장 빠른 복구:

```bash
task db:reset
task dev
```

주의: `task db:reset`은 로컬 PostgreSQL 볼륨을 삭제하므로 데이터가 초기화됩니다.

`ERROR: JAVA_HOME is set to an invalid directory` 오류가 나오면:

```bash
unset JAVA_HOME
task backend:run
```

또는 JDK 17 경로를 직접 지정해서 실행:

```bash
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew bootRun
```

## Wallet Registration

초기 동기화 범위를 줄이기 위해 `startBlock`을 함께 전달할 수 있습니다.

```json
{
  "address": "0x1111111111111111111111111111111111111111",
  "label": "client-wallet",
  "startBlock": 19000000
}
```

## Admin Correction

Sprint 3 admin correction은 인증된 admin 계정으로만 실행됩니다. 사용하려면 루트 `.env`에 아래 값을 설정해야 합니다.

```bash
ADMIN_CORRECTION_USERS=
ADMIN_CORRECTION_USERS_FILE=.secrets/admin-correction-users
ADMIN_CORRECTION_SESSION_SECRET=replace-with-a-long-random-secret
BACKEND_API_BASE_URL=http://localhost:8080
```

운영 환경에서는 `ADMIN_CORRECTION_USERS_FILE`을 권장합니다. 파일은 줄 단위 `username:{id}encodedPassword` 형식을 사용하며, 예시는 아래와 같습니다.

```text
ops-kim:{bcrypt}$2a$10$...
ops-lee:{bcrypt}$2a$10$...
```

`ADMIN_CORRECTION_USERS=username:password,...`는 로컬 개발용 fallback 으로만 유지됩니다. file source는 인증 시점마다 다시 읽기 때문에 password rotation / offboarding을 재시작 없이 반영할 수 있습니다.

`BACKEND_API_BASE_URL`은 frontend의 일반 `/api/*` rewrite와 admin correction relay가 함께 사용합니다.

지갑 화면 상단의 `Admin Correction Session`에서 1회 인증하면, 이후 correction request는 브라우저 Basic Auth 대신 server-side session cookie를 통해 backend로 relay 됩니다. audit actor는 입력값이 아니라 인증된 admin principal로 기록되며, credential rotation / offboarding으로 backend가 `401`을 반환하면 stale session cookie도 자동으로 정리됩니다.

## Known Limitation

- 표준 JSON-RPC(`eth_getLogs`, `eth_getBlockByNumber`) 기반 수집에서는 contract initiated ETH transfer(internal transaction)가 기본적으로 누락될 수 있습니다.
