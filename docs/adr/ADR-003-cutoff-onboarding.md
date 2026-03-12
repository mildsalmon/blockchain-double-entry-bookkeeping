# ADR-003: Cutoff 온보딩 전략 (최소 설계)

## 상태: 확정

## 맥락

지갑 등록 시 과거 전체 블록을 스캔하면 시간이 과도하게 소요된다.
이전 프로젝트에서 cutoff 기능이 IngestWalletUseCase(324줄) + 7단계 상태머신으로
비대해진 교훈이 있다.

## 결정

**컬럼 2개, 테이블 0개, 전용 서비스 0개.**

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `cutoff_block` | `BIGINT` | 잔고 스냅샷 시점 블록 (NULL이면 미수집) |
| `cutoff_balance` | `NUMERIC(38,18)` | 해당 블록 시점 네이티브 토큰 잔고 |

## sync_status 전이

```
PENDING → ONBOARDING → IDLE → SYNCING → IDLE
                  ↘ FAILED ↗
```

## 경합 방지

- CAS 패턴: `UPDATE wallets SET sync_status='ONBOARDING' WHERE id=? AND sync_status='PENDING'`
- 앱 재시작 시: ONBOARDING→PENDING 복원

## 하드 경계 (스코프 크립 방지)

- cutoff 전용 신규 테이블: **0개**
- cutoff 전용 서비스 클래스: **0개** (SyncPipelineUseCase 첫 분기로 처리)
- ERC-20 잔고 스냅샷: Phase 1에서 **금지** (네이티브 토큰만)
- preflight / summaryHash / Admin Correction: **금지**

## 백필 (Phase 2+)

- `POST /api/wallets/{id}/backfill`
- cutoff_block 아래 범위만 대상, 정상 동기화와 충돌 없음
- ON CONFLICT DO NOTHING으로 멱등성 보장

## 거부한 대안

- **wallet_balance_snapshots 전용 테이블**: 스코프 크립 진입점
- **CutoffSnapshotService 전용 서비스**: 이전 프로젝트 반복
- **전체 과거 블록 스캔**: 시간 과도, 불필요
