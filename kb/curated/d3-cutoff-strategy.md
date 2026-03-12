---
id: d3-cutoff-strategy
title: "D3: Cutoff 온보딩 설계 확정"
status: curated
confidence: 0.9
tags: [topic/blockchain, topic/architecture, mode/convergent]
created: 2026-03-11
---

## 결정: 최소 cutoff 설계 (컬럼 2개, 테이블 0개, 전용 서비스 0개)

### Phase 1a
- wallets.cutoff_block BIGINT (PRD start_block → cutoff_block 리네이밍)
- sync_status에 ONBOARDING 추가 (PENDING→ONBOARDING→IDLE)

### Phase 1b
- wallets.cutoff_balance NUMERIC(38,18) 컬럼 추가 (V3/V4 migration)
- SyncPipelineUseCase "첫 동기화" 분기: cutoffBlock == null → eth_getBalance(safeBlock)
- 스케줄러 CAS: UPDATE ... WHERE sync_status='PENDING'
- 앱 시작 시 ONBOARDING→PENDING 복원 (@PostConstruct)
- UI: "TX 수집 시작: 블록 #X, 이전 TX 미수집" 안내

### Phase 2+ (백필)
- POST /api/wallets/{id}/backfill (백필은 cutoff_block 아래, 정상 동기화는 위)
- backfill_status 컬럼 (V20 대역)

### 하드 경계 (스코프 크립 방지)
- 신규 테이블 0개 (wallet_balance_snapshots 금지)
- cutoff 전용 서비스 0개 (SyncPipeline의 첫 분기로 처리)
- ERC-20 잔고 스냅샷 금지 (네이티브 토큰만)
- preflight/summaryHash/Admin Correction 금지

### 이전 프로젝트 교훈
archive의 IngestWalletUseCase(324줄) + 7단계 상태머신 = 스코프 크립의 전형
이번: 2단계 전이 + 컬럼 2개 = 전부
