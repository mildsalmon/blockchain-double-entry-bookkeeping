# DB Schema Decisions

## 사용 도구: Flyway

- Spring Boot 네이티브 통합 (auto-configuration)
- Phase별 V 번호 대역: Phase 1(V1~V19), Phase 2(V20~V39), Phase 3(V40~V59), Phase 4(V60~V79)
- 기존 migration 수정 금지. 새 migration 추가만.
- Phase 2 이후 규모가 커지면 Atlas 재검토 가능 (Flyway baseline으로 전환 용이)

## Cutoff 온보딩 설계

### 컬럼

| 컬럼 | 타입 | Phase | 설명 |
|------|------|-------|------|
| `cutoff_block` | `BIGINT` | 1a | 잔고 스냅샷 블록. NULL이면 아직 스냅샷 안 찍음. |
| `cutoff_balance` | `NUMERIC(38,18)` | 1b | cutoff_block 시점 네이티브 토큰 잔고. |

### sync_status 전이

```
PENDING → ONBOARDING → IDLE → SYNCING → IDLE
                  ↘ FAILED ↗
```

- PENDING: 등록 직후. 스케줄러 대기.
- ONBOARDING: 첫 동기화 사이클 진행 중 (cutoff 스냅샷).
- IDLE: 정상 대기.
- SYNCING: 증분 동기화 진행 중.
- FAILED: 실패. fail_count 기반 백오프.

### 경합 방지

- 스케줄러가 PENDING 월렛 처리 시 CAS:
  `UPDATE wallets SET sync_status='ONBOARDING' WHERE id=? AND sync_status='PENDING'`
- 영향 행 0이면 이미 다른 스레드가 처리 중 → skip
- 앱 재시작 시: `UPDATE wallets SET sync_status='PENDING' WHERE sync_status='ONBOARDING'`

### 하드 경계 (스코프 크립 방지)

- cutoff 관련 신규 테이블: 0개
- cutoff 전용 서비스 클래스: 0개 (SyncPipelineUseCase 첫 분기로 처리)
- ERC-20 잔고 스냅샷: Phase 1에서 금지 (네이티브 토큰만)
- preflight/summaryHash/Admin Correction: 금지

### 백필(Backfill)

- Phase 2+ 에서 구현
- `POST /api/wallets/{id}/backfill`
- 백필은 cutoff_block 아래 범위, 정상 동기화는 위 범위 → 충돌 없음
- ON CONFLICT DO NOTHING으로 멱등성 보장

## Exchange DB (Phase 2+)

- 접근 방식: `ExchangeTransactionPort` 인터페이스로 추상화 (ADR-004 참조)
- Phase 2 기본: **Databricks** (기존 `fact_crypto_transactions` 모델 활용)
- Athena 이식 완료 시: `exchange.db.adapter=athena`로 전환
- MySQL은 폴백/대안 (`exchange.db.adapter=mysql`)
- `@ConditionalOnProperty("exchange.db.adapter")` 기반 어댑터 로딩
- 로컬 개발: Databricks Community Edition 또는 Mock 어댑터
- 보안: read replica + 전용 RO 계정 + @Transactional(readOnly=true)
- 스키마: 사용자의 fact_crypto_transactions 기반

## 토큰 추적 (Phase 1b)

- `wallet_tracked_tokens` 테이블: 월렛별 추적 ERC-20 토큰 등록
- 지갑 등록 후 추적할 토큰 contract address + symbol + decimals 등록
- ERC-20 수집 시 등록된 토큰만 주요 화면에 표시, 미등록 토큰은 raw_data에 보존
