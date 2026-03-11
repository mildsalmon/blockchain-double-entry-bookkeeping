# Wallet Auto-Discovery Fast-Follow Backlog

## Purpose

이번 문서는 tribunal 리뷰에서 남은 non-blocking 항목을 빠르게 추적하기 위한 backlog 이다.

현재 상태:
- cutoff-first onboarding 은 ship 가능
- 기존 blocker 였던 existing cutoff retry, wallet + sign-off 원자성은 해소됨
- 아래 항목은 fast-follow 또는 운영 배포 주의사항으로 분리

## P1: Symbol Hygiene

### Problem

`TokenMetadataService.normalizeSymbol()` 이 `ERC20` exact 만 제외하고 `ERC20-xxxx` 패턴은 통과시킨다.

영향:
- cutoff visibility 경로에서 사람이 읽기 어려운 fallback 라벨이 다시 나올 수 있음

### Target

- `ERC20-` prefix 패턴도 unsafe fallback 으로 취급
- cached/on-chain symbol 이 없으면 `ERR` 또는 더 명시적인 safe fallback 사용

### Suggested Touch Points

- [TokenMetadataService.kt](/Users/mildsalmon/source_code/blockchain-double-entry-bookkeeping/backend/src/main/kotlin/com/example/ledger/domain/service/TokenMetadataService.kt)
- [WalletCutoffInsightsService.kt](/Users/mildsalmon/source_code/blockchain-double-entry-bookkeeping/backend/src/main/kotlin/com/example/ledger/application/usecase/WalletCutoffInsightsService.kt)

## P1: Audit Log Index Rollout Safety

### Problem

`V10__audit_log_lookup_index.sql` 은 일반 `CREATE INDEX` 이다.

영향:
- 운영 `audit_log` 데이터가 커질 경우 배포 시 write contention 가능성

### Target

- 운영 환경 규모 확인
- 필요 시 safer rollout 전략 정의
  - maintenance window
  - pre-deploy index
  - online/concurrent index support 여부 검토

### Suggested Touch Points

- [V10__audit_log_lookup_index.sql](/Users/mildsalmon/source_code/blockchain-double-entry-bookkeeping/backend/src/main/resources/db/migration/V10__audit_log_lookup_index.sql)
- 배포 runbook / infra docs

## P2: Wallet Polling Cost

### Problem

wallet list/status polling 은 side effect 는 제거됐지만 wallet 수에 비례한 enrichment query 비용이 있다.

영향:
- wallet 수 증가 시 `/api/wallets` polling cost 상승

### Target

- polling 빈도/조건 재검토
- latest sign-off / discovered token summary precompute 또는 cache 검토
- list view 와 detail view enrichment 분리 가능성 검토

### Suggested Touch Points

- [IngestWalletUseCase.kt](/Users/mildsalmon/source_code/blockchain-double-entry-bookkeeping/backend/src/main/kotlin/com/example/ledger/application/usecase/IngestWalletUseCase.kt)
- [WalletCutoffInsightsService.kt](/Users/mildsalmon/source_code/blockchain-double-entry-bookkeeping/backend/src/main/kotlin/com/example/ledger/application/usecase/WalletCutoffInsightsService.kt)

## P2: Sign-Off Evidence Visibility

### Problem

backend 는 `summaryHash`, `seededTokenCount` 를 응답하지만 UI 는 reviewer / reviewedAt 만 보여준다.

영향:
- 운영자가 화면만 보고 sign-off 근거를 빠르게 재검증하기 어렵다

### Target

- wallet status UI 에 sign-off evidence 핵심 요약 표시
  - reviewer
  - reviewedAt
  - seeded token count
  - summary hash or truncated hash

### Suggested Touch Points

- [WalletDtos.kt](/Users/mildsalmon/source_code/blockchain-double-entry-bookkeeping/backend/src/main/kotlin/com/example/ledger/application/dto/WalletDtos.kt)
- [SyncStatus.tsx](/Users/mildsalmon/source_code/blockchain-double-entry-bookkeeping/frontend/src/components/SyncStatus.tsx)

## Priority Recommendation

1. Symbol hygiene
2. Audit log index rollout safety
3. Sign-off evidence visibility
4. Wallet polling optimization
