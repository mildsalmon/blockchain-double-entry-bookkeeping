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
task down            # DB 종료
task db:logs         # DB 로그 확인
```

## Raw Commands (without Task)

```bash
docker compose up -d
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun
cd frontend && npm install && npm run dev
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

## Known Limitation

- 표준 JSON-RPC(`eth_getLogs`, `eth_getBlockByNumber`) 기반 수집에서는 contract initiated ETH transfer(internal transaction)가 기본적으로 누락될 수 있습니다.
