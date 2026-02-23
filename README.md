# Blockchain Double-Entry Bookkeeping

Ethereum 트랜잭션을 복식부기 분개로 생성하고, 회계사가 검토/수정/승인/내보내기할 수 있는 감사 워크벤치입니다.

## Run

```bash
docker-compose up -d
cd backend && ./gradlew bootRun
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
