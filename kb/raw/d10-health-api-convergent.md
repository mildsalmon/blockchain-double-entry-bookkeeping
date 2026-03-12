---
id: d10-health-api-convergent
title: Health API 통합 설계 (d6+d9 결합)
status: raw
tags: [mode/convergent, topic/architecture, topic/product]
created: 2026-03-12
confidence: 0.6
---

## 제안 (CONVERGENT — COMBINATION of d6 + d9)

d6(스케줄러 관측성) + d9(신호등 정의)를 결합한 Health API 설계.

## 엔드포인트

```
GET /api/health/sync → SyncHealthResponse

SyncHealthResponse {
  chains: [{
    chainId: "ETH",
    status: "GREEN" | "YELLOW" | "RED",
    lastSyncedBlock: 19500000,
    latestBlock: 19500025,         // 마지막 알려진 최신 블록
    lagBlocks: 25,                 // latestBlock - lastSyncedBlock
    lastSuccessAt: "2026-03-12T10:00:00Z",
    failCount: 0,
    activeWallets: 3,
    message: null                  // RED/YELLOW 시 사유
  }],
  overallStatus: "GREEN",
  lastCheckAt: "2026-03-12T10:05:00Z"
}
```

## 신호등 로직 (서버사이드)

```
GREEN:  모든 월렛 IDLE, lag < 2배치(~10분), fail_count=0
YELLOW: SYNCING 중 OR lag 10~30분 OR 0 < fail_count < 5
RED:    FAILED 존재 OR lag > 30분 OR fail_count >= 5 OR 체인 비활성
```

## Phase 1b 구현 범위

- `/api/health/sync` 엔드포인트
- sync_cursors + wallets 기반 계산
- 프론트엔드 대시보드에서 폴링 (30초 간격)

## 근거

PRD 시나리오 A "일일 감사 체크"의 첫 단계가 신호등 확인.
백엔드 API 없이 프론트엔드가 sync_cursors를 직접 쿼리하면 비즈니스 로직 분산.
