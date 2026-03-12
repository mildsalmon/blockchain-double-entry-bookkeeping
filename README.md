# Chain Watcher — 멀티체인 TX 수집-대조 도구

거래소가 보유한 핫/콜드 월렛의 멀티체인 온체인 트랜잭션을 5~10분 주기로 수집하고,
서비스 DB 기록과 대조하여 누락/불일치를 자동 탐지하는 내부 회계감사 도구.

---

## 아키텍처

```
┌──────────────────────────────────────────────────────────────┐
│                     Spring Boot App                          │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │           Central Batch Scheduler (@Scheduled)         │  │
│  └────┬────┬────┬────┬────┬───────────────────────────────┘  │
│       │    │    │    │    │                                   │
│  [ETH] [POL] [BSC] [BTC] [DOGE]                              │
│       │    │    │    │    │                                   │
│  ┌────▼────▼────▼────▼────▼───────────────────────────────┐  │
│  │           ChainAdapter (interface)                     │  │
│  │           ├── EvmChainAdapter (ETH/POL/BSC)            │  │
│  │           └── UtxoChainAdapter (BTC/DOGE)              │  │
│  └──────────────────┬─────────────────────────────────────┘  │
│                     │                                        │
│  ┌──────────────────▼─────────────────────────────────────┐  │
│  │           PostgreSQL 16                                │  │
│  │  chains | wallets | raw_transactions | sync_cursors    │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  ExchangeTransactionPort (Phase 2)                     │  │
│  │  ├── DatabricksExchangeAdapter (기본)                   │  │
│  │  ├── AthenaExchangeAdapter (이식 후)                    │  │
│  │  └── MySqlExchangeAdapter (폴백)                        │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │           Next.js 15 Frontend                          │  │
│  │  대시보드 | TX 검색 | 대조 결과 | 내보내기              │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

### 포트/어댑터 패턴 (ADR-002)

```
adapter/inbound → application/port/input → application/service → application/port/output ← adapter/outbound
                                            domain/service
                                            domain/model
```

domain은 어떤 adapter에도 의존하지 않는다. 구현체가 1개뿐이여도 인터페이스를 반드시 생성한다.

---

## 기술 스택

| 계층 | 기술 | 버전 |
|------|------|------|
| Language | Kotlin | 2.1.x |
| Framework | Spring Boot | 3.4.x |
| DB (주) | PostgreSQL (변경 가능) | 16 |
| DB (거래소) | Databricks 기본 (Athena 이식 예정, MySQL 폴백) | - |
| Migration | Flyway | Spring Boot 관리 |
| Frontend | Next.js + React | 15.x / 18 |
| Container | Docker Compose | - |
| Test | JUnit 5, Testcontainers | - |

---

## Phase 로드맵

| Phase | 범위 | 기간 |
|-------|------|------|
| **1a** (현재) | 클린 리셋 + 멀티체인 스키마 | 3~4일 |
| **1b** | EVM 멀티체인 TX 수집 | 7~10일 |
| **2** | 서비스 DB 대조 + 내보내기 | 2~3주 |
| **3** | UTXO 체인 + 알림 | 3~4주 |
| **4** | Gas Fee / 잔고 역산 / Internal TX | 2~3주 |

---

## 빠른 시작

```bash
git clone <repo>
cp .env.example .env
# .env 편집 (RPC URL 등)
docker compose up -d
```

- Backend: http://localhost:8080
- Frontend: http://localhost:3000

---

## 핵심 설계 결정 (ADR)

| ADR | 결정 | 상세 |
|-----|------|------|
| [ADR-001](docs/adr/ADR-001-flyway-migration.md) | Flyway DB Migration | Spring Boot 네이티브 통합 |
| [ADR-002](docs/adr/ADR-002-port-adapter-pattern.md) | 포트/어댑터 패턴 | SOLID 기반, 헥사고날 명명 회피 |
| [ADR-003](docs/adr/ADR-003-cutoff-onboarding.md) | Cutoff 온보딩 | 2컬럼, 0테이블, 0서비스 |
| [ADR-004](docs/adr/ADR-004-exchange-db-abstraction.md) | Exchange DB 추상화 | Port 인터페이스로 플랫폼 독립 |
| [ADR-005](docs/adr/ADR-005-evm-two-track-collection.md) | EVM 2-Track 수집 | eth_getBlock + eth_getLogs 병렬 |
| [ADR-006](docs/adr/ADR-006-utxo-dual-strategy.md) | UTXO 이중 전략 | 자체 노드 vs Public API |
| [ADR-007](docs/adr/ADR-007-batch-scheduling.md) | 배치 스케줄링 | @Scheduled, 체인간 병렬 |
| [ADR-008](docs/adr/ADR-008-internal-tx-strategy.md) | Internal TX 전략 | Phase 4 이연, 잔고 대사 간접 감지 |

---

## 프로젝트 구조

```
backend/src/main/kotlin/com/example/txcollector/
  adapter/
    inbound/       # REST Controller, Scheduler (외부 → 앱)
    outbound/      # JPA Repository, RPC Client, Exchange DB (앱 → 외부)
  application/
    port/
      input/       # UseCase 인터페이스 (인바운드 포트)
      output/      # Repository, ChainAdapter 인터페이스 (아웃바운드 포트)
    service/       # UseCase 구현체
    dto/           # Request/Response DTOs
  domain/
    service/       # 도메인 비즈니스 로직
    model/         # Chain, Wallet, RawTransaction, SyncCursor
  config/          # SchedulerConfig, DataSourceConfig

frontend/src/
  app/             # Next.js App Router pages
  components/      # Reusable UI components
  lib/             # API client
  types/           # TypeScript types

docs/
  adr/             # Architecture Decision Records
  known-edge-cases.md
  schema-decisions.md
```

---

## 참고 문서

- [PRD.md](PRD.md) — 상세 제품 요구사항
- [CLAUDE.md](CLAUDE.md) — AI 개발 가이드라인
- [AGENTS.md](AGENTS.md) — AI Agent 공용 가이드라인
- [docs/known-edge-cases.md](docs/known-edge-cases.md) — 알려진 엣지케이스
