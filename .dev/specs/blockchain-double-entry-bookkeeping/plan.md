---
title: "Blockchain Double-Entry Bookkeeping — Technical Plan"
spec: ".dev/specs/blockchain-double-entry-bookkeeping/spec.md"
status: approved
feature: "blockchain-double-entry-bookkeeping"
date: "2026-02-22"
---

## Architecture

빈 저장소에서 시작하는 그린필드 프로젝트. 모노레포 구조로 백엔드(Kotlin Spring Boot)와 프론트엔드(Next.js)를 관리한다.

```
blockchain-double-entry-bookkeeping/
├── backend/                          # Kotlin + Spring Boot
│   ├── src/main/kotlin/
│   │   └── com/example/ledger/
│   │       ├── domain/               # Domain Layer
│   │       │   ├── model/            # 엔티티, Value Objects
│   │       │   ├── service/          # 비즈니스 로직
│   │       │   └── port/             # 인터페이스 (Driven Ports)
│   │       ├── application/          # Application Layer
│   │       │   ├── usecase/          # Use Cases (Driving Ports)
│   │       │   └── dto/              # Request/Response DTOs
│   │       ├── adapter/              # Adapter Layer
│   │       │   ├── web/              # REST Controllers
│   │       │   ├── persistence/      # JPA Repositories
│   │       │   ├── alchemy/          # Alchemy API Client
│   │       │   ├── coingecko/        # CoinGecko API Client
│   │       │   └── export/           # CSV/Excel Exporter
│   │       └── config/               # Spring Config
│   ├── src/main/resources/
│   │   ├── db/migration/             # Flyway migrations
│   │   └── application.yml
│   └── build.gradle.kts
├── frontend/                         # Next.js + TypeScript
│   ├── src/
│   │   ├── app/                      # App Router pages
│   │   ├── components/               # React components
│   │   ├── lib/                      # API client, utils
│   │   └── types/                    # TypeScript types
│   ├── package.json
│   └── tsconfig.json
├── docker-compose.yml
└── README.md
```

**Layer Separation**:
- **Domain**: 도메인 모델(RawTransaction, AccountingEvent, JournalEntry, CostBasisLot), 서비스(ClassificationService, LedgerService, FifoService), 포트(TransactionRepository, PricePort, ClassifierPlugin)
- **Port (Driving)**: Use Cases — IngestWalletUseCase, ClassifyTransactionsUseCase, GenerateLedgerUseCase, ExportUseCase
- **Port (Driven)**: TransactionRepository, AccountingEventRepository, JournalRepository, PricePort, BlockchainDataPort
- **Adapter**: AlchemyAdapter (BlockchainDataPort), CoinGeckoAdapter (PricePort), JpaRepositories (Repository ports), RestControllers (Driving), CsvExporter, ExcelExporter

## Tasks

### Task 1: 프로젝트 스캐폴딩 + Docker Compose

- **Files**:
  - `backend/build.gradle.kts` (신규)
  - `backend/settings.gradle.kts` (신규)
  - `backend/src/main/kotlin/com/example/ledger/LedgerApplication.kt` (신규)
  - `backend/src/main/resources/application.yml` (신규)
  - `backend/src/main/resources/application-local.yml` (신규)
  - `frontend/package.json` (신규)
  - `frontend/tsconfig.json` (신규)
  - `frontend/next.config.js` (신규)
  - `frontend/tailwind.config.ts` (신규)
  - `frontend/src/app/layout.tsx` (신규)
  - `frontend/src/app/page.tsx` (신규)
  - `docker-compose.yml` (신규)
  - `.gitignore` (신규)
  - `.env.example` (신규)
- **MUST DO**:
  - Kotlin + Spring Boot 3.x + Gradle Kotlin DSL 프로젝트 생성
  - 의존성: Spring Web, Spring Data JPA, Flyway, Jackson, PostgreSQL Driver
  - Next.js 14+ + TypeScript + Tailwind CSS 프로젝트 생성
  - Docker Compose: PostgreSQL 16 서비스 정의
  - `.env.example`에 ALCHEMY_API_KEY, COINGECKO_API_KEY 자리 포함
  - 빌드 및 실행 확인 (backend `./gradlew bootRun`, frontend `npm run dev`)
  - **[CR-008]** Spring Boot CORS 설정: `WebMvcConfigurer.addCorsMappings()`로 `localhost:3000` 허용, 또는 `next.config.js`에 `/api/*` → `localhost:8080` 프록시 rewrite 설정
- **MUST NOT DO**:
  - 비즈니스 로직 작성하지 않음
  - 실제 API 키를 코드에 포함하지 않음
- **Acceptance Criteria**:
  - [ ] `docker-compose up -d` 로 PostgreSQL 기동 확인
  - [ ] `./gradlew bootRun` 으로 Spring Boot 기동 (health check 200)
  - [ ] `npm run dev` 로 Next.js 기동 (localhost:3000 접속 확인)
  - [ ] `./gradlew test` 실행 시 에러 없음
  - [ ] **[CR-008]** 프론트엔드에서 백엔드 API 호출 시 CORS 에러 없음

---

### Task 2a: DB 마이그레이션 — 코어 테이블

- **Files**:
  - `backend/src/main/resources/db/migration/V1__core_tables.sql` (신규)
- **MUST DO**:
  - `wallets` 테이블 생성 (address UNIQUE, label, sync_status, last_synced_at, **last_synced_block BIGINT** [CR-014])
  - `accounts` 테이블 생성 (code UNIQUE, name, category, is_system)
  - `price_cache` 테이블 생성 (token_symbol, price_date, price_krw, source, UNIQUE constraint)
  - `audit_log` 테이블 생성 (entity_type, entity_id, action, old_value JSONB, new_value JSONB)
- **MUST NOT DO**:
  - Layer 1/2/3 테이블은 이 태스크에서 생성하지 않음
- **Acceptance Criteria**:
  - [ ] `./gradlew bootRun` 시 Flyway 마이그레이션 자동 실행
  - [ ] 4개 테이블이 PostgreSQL에 생성됨

### Task 2b: DB 마이그레이션 — Layer 1 (Raw)

- **Files**:
  - `backend/src/main/resources/db/migration/V2__raw_transactions.sql` (신규)
- **MUST DO**:
  - `raw_transactions` 테이블 생성 (tx_hash UNIQUE, raw_data JSONB, block_number, block_timestamp, tx_status)
  - `wallet_address`에 인덱스 생성
- **Acceptance Criteria**:
  - [ ] 마이그레이션 성공, 테이블 생성 확인

### Task 2c: DB 마이그레이션 — Layer 2 (Normalized)

- **Files**:
  - `backend/src/main/resources/db/migration/V3__accounting_events.sql` (신규)
- **MUST DO**:
  - `accounting_events` 테이블 생성 (FK → raw_transactions, event_type, classifier_id, token 정보, price_krw, price_source)
  - `NUMERIC(78,0)` for amount_raw (wei), `NUMERIC(38,18)` for amount_decimal
- **Acceptance Criteria**:
  - [ ] 마이그레이션 성공, FK 무결성 확인

### Task 2d: DB 마이그레이션 — Layer 3 (Ledger)

- **Files**:
  - `backend/src/main/resources/db/migration/V4__ledger_tables.sql` (신규)
- **MUST DO**:
  - `journal_entries` 테이블 생성 (FK → accounting_events, **raw_transaction_id BIGINT FK → raw_transactions** [CR-010], entry_date, description, status)
  - `journal_lines` 테이블 생성 (FK → journal_entries, account_code **FK → accounts(code)** [CR-003], debit_amount, credit_amount)
  - `cost_basis_lots` 테이블 생성 (wallet_address, token_symbol, quantity, remaining_qty, unit_cost_krw)
  - **[CR-018]** `journal_entries(entry_date, status)` 및 `journal_lines(account_code)` 인덱스 생성 (NFR-2: 2초 이내 페이지 로딩)
- **Acceptance Criteria**:
  - [ ] 마이그레이션 성공, 모든 FK 관계 확인

### Task 2e: 시드 데이터 — 기본 계정과목

- **Files**:
  - `backend/src/main/resources/db/migration/V5__seed_accounts.sql` (신규)
- **MUST DO**:
  - 기본 계정과목 7건 INSERT (FR-13):
    - `자산:암호화폐:ETH`, `자산:암호화폐:ERC20:*` (템플릿), `비용:가스비`, `비용:거래수수료`, `수익:실현이익`, `비용:실현손실`, `수익:에어드롭`
  - `is_system = true` 설정
- **MUST NOT DO**:
  - 시스템 계정과목 삭제 가능하게 하지 않음
- **Acceptance Criteria**:
  - [ ] 앱 기동 시 `accounts` 테이블에 7건 기본 데이터 존재

---

### Task 3: 도메인 모델 + 포트 인터페이스

- **Files**:
  - `backend/src/main/kotlin/com/example/ledger/domain/model/` (신규 — 다수 파일)
    - `Wallet.kt`, `RawTransaction.kt`, `AccountingEvent.kt`, `EventType.kt`
    - `JournalEntry.kt`, `JournalLine.kt`, `JournalStatus.kt`
    - `Account.kt`, `AccountCategory.kt`
    - `CostBasisLot.kt`, `PriceInfo.kt`, `AuditLogEntry.kt`
    - `DecodedTransaction.kt` — ABI 디코딩 결과를 담는 도메인 모델 [CR-005]
  - `backend/src/main/kotlin/com/example/ledger/domain/port/` (신규)
    - `BlockchainDataPort.kt` — 블록체인 데이터 수집 인터페이스
    - `PricePort.kt` — 가격 조회 인터페이스
    - `ClassifierPlugin.kt` — 트랜잭션 분류기 플러그인 인터페이스
    - `WalletRepository.kt`, `RawTransactionRepository.kt`
    - `AccountingEventRepository.kt`, `JournalRepository.kt`
    - `AccountRepository.kt`, `CostBasisLotRepository.kt`
    - `PriceCacheRepository.kt`, `AuditLogRepository.kt`
  - `backend/src/main/kotlin/com/example/ledger/domain/service/` (신규)
    - `AuditService.kt` — 감사 추적 로깅 서비스 인터페이스
- **MUST DO**:
  - 모든 도메인 모델을 Kotlin data class / sealed class로 정의
  - 포트(인터페이스)만 정의, 구현체는 adapter에서
  - **[CR-005]** `ClassifierPlugin` 인터페이스: `fun classify(decodedTx: DecodedTransaction): List<AccountingEvent>` — RawTransaction이 아닌 디코딩된 도메인 모델을 입력으로 받음
  - `AuditService` 인터페이스: `fun log(entityType, entityId, action, oldValue, newValue)`
  - `JournalEntry`에 불변식: 차변 합 = 대변 합 검증 로직
  - **[CR-004]** `JournalEntry`에 불변식: `fun validateEditable()` — status == APPROVED이면 예외 발생
- **MUST NOT DO**:
  - 도메인 레이어에서 외부 인프라(JPA, HTTP) 직접 참조하지 않음
  - 구현체를 이 태스크에서 작성하지 않음
- **Acceptance Criteria**:
  - [ ] 모든 도메인 모델 컴파일 성공
  - [ ] 포트 인터페이스 정의 완료
  - [ ] JournalEntry 차변=대변 불변식 단위 테스트 통과
  - [ ] **[CR-004]** JournalEntry APPROVED 상태에서 수정 시도 → 예외 발생 단위 테스트 통과

---

### Task 4: 복원력 패턴 (Resilience Infrastructure)

- **Files**:
  - `backend/src/main/kotlin/com/example/ledger/adapter/common/RetryConfig.kt` (신규)
  - `backend/src/main/kotlin/com/example/ledger/adapter/common/RateLimiter.kt` (신규)
  - `backend/src/main/kotlin/com/example/ledger/config/TransactionConfig.kt` (신규) [CR-002]
- **MUST DO**:
  - 재시도 로직: 지수 백오프 (최대 3회), 설정 가능한 딜레이
  - 레이트 리미터: 분당 호출 수 제한 (CoinGecko 30/min 대응)
  - Spring `@Retryable` 또는 자체 구현 (코루틴 호환)
  - **[CR-002]** NFR-4 준수: 금융 데이터 변경 서비스에 `@Transactional(isolation = Isolation.SERIALIZABLE)` 적용 규칙 정의. 대상: LedgerService, FifoService, GainLossService, JournalEntry update/approve
- **MUST NOT DO**:
  - 특정 API에 종속적인 로직 포함하지 않음 (범용 인프라)
- **Acceptance Criteria**:
  - [ ] 재시도 로직 단위 테스트: 실패 3회 후 예외 전파 확인
  - [ ] 레이트 리미터 단위 테스트: 초과 호출 시 대기 확인
  - [ ] **[CR-002]** TransactionConfig에 SERIALIZABLE 격리 수준 설정 확인

---

### Task 5: Alchemy 인제스션 어댑터

- **Files**:
  - `backend/src/main/kotlin/com/example/ledger/adapter/alchemy/AlchemyClient.kt` (신규)
  - `backend/src/main/kotlin/com/example/ledger/adapter/alchemy/AlchemyAdapter.kt` (신규)
  - `backend/src/main/kotlin/com/example/ledger/adapter/alchemy/dto/` (신규)
  - `backend/src/main/kotlin/com/example/ledger/adapter/persistence/RawTransactionJpaRepository.kt` (신규)
  - `backend/src/main/kotlin/com/example/ledger/adapter/persistence/WalletJpaRepository.kt` (신규)
  - `backend/src/main/kotlin/com/example/ledger/adapter/persistence/entity/` (신규 — JPA 엔티티)
- **MUST DO**:
  - `BlockchainDataPort` 구현: Alchemy `getAssetTransfers` 호출
  - 페이지네이션 (`pageKey`) 처리 (FR-4)
  - 응답 JSON 원본을 `raw_transactions.raw_data`에 저장 (Layer 1)
  - 실패한 트랜잭션 포함 (FR-5): `getAssetTransfers`에서 누락 시 별도 `eth_getTransactionReceipt` 호출
  - `wallets.sync_status` 상태 관리 (PENDING → SYNCING → COMPLETED/FAILED)
  - Task 4의 재시도 패턴 적용
- **MUST NOT DO**:
  - 분류/가격 로직을 이 어댑터에 포함하지 않음
  - 실제 Alchemy API 호출이 포함된 테스트를 단위 테스트로 작성하지 않음 (mock 사용)
- **Acceptance Criteria**:
  - [ ] Mock Alchemy 응답으로 raw_transactions 저장 단위 테스트 통과
  - [ ] 페이지네이션 테스트: 2페이지 이상 처리 확인
  - [ ] 실패 트랜잭션 수집 확인
  - [ ] sync_status 상태 전이 테스트

---

### Task 6: CoinGecko 가격 어댑터

- **Files**:
  - `backend/src/main/kotlin/com/example/ledger/adapter/coingecko/CoinGeckoClient.kt` (신규)
  - `backend/src/main/kotlin/com/example/ledger/adapter/coingecko/CoinGeckoAdapter.kt` (신규)
  - `backend/src/main/kotlin/com/example/ledger/adapter/coingecko/TokenIdMapper.kt` (신규)
  - `backend/src/main/kotlin/com/example/ledger/adapter/persistence/PriceCacheJpaRepository.kt` (신규)
- **MUST DO**:
  - `PricePort` 구현: **[CR-007]** CoinGecko `/coins/{id}/market_chart/range` API 호출 (단일 날짜 `/history` 대신 날짜 범위 배치 조회로 API 호출 횟수를 O(dates×tokens) → O(tokens)로 절감)
  - `TokenIdMapper`: ERC-20 contract address → CoinGecko coin ID 매핑 (주요 토큰 사전 등록)
  - 가격 캐싱: `price_cache` 테이블 조회 후 미스 시 API 호출 (FR-28)
  - KRW 환산 직접 지원 (FR-27)
  - 가격 미확인 토큰 → price = 0, source = "UNKNOWN" (FR-29)
  - Task 4의 레이트 리미터 적용 (30 calls/min)
- **MUST NOT DO**:
  - 실시간 가격 스트리밍 구현하지 않음
- **Acceptance Criteria**:
  - [ ] Mock CoinGecko 응답으로 KRW 가격 반환 테스트
  - [ ] 캐시 히트 시 API 미호출 확인
  - [ ] 미지원 토큰 → 0 + UNKNOWN 플래그 테스트
  - [ ] 레이트 리밋 적용 확인
  - [ ] **[CR-007]** 500건 고유 가격 조회가 2분 이내 완료되는 성능 테스트

---

### Task 7: ABI 디코딩 + web3j 통합

- **Files**:
  - `backend/src/main/kotlin/com/example/ledger/adapter/web3/EventDecoder.kt` (신규)
  - `backend/src/main/kotlin/com/example/ledger/adapter/web3/abi/` (신규)
    - `ERC20ABI.kt` — ERC-20 Transfer 이벤트 ABI
    - `UniswapV2ABI.kt` — Uniswap V2 Swap 이벤트 ABI
    - `UniswapV3ABI.kt` — Uniswap V3 Swap 이벤트 ABI
- **MUST DO**:
  - web3j 의존성 추가
  - ERC-20 `Transfer(address,address,uint256)` 이벤트 디코딩
  - Uniswap V2 Pool `Swap(address,uint256,uint256,uint256,uint256,address)` 이벤트 디코딩
  - Uniswap V3 Pool `Swap(address,address,int256,int256,uint160,uint128,int24)` 이벤트 디코딩
  - 트랜잭션 receipt의 logs에서 관련 이벤트 추출
  - **[CR-005]** `RawTransaction` → `DecodedTransaction` 변환 로직 구현. DecodedTransaction은 도메인 모델이며, 디코딩된 이벤트(TransferEvent, SwapEvent 등)를 포함. 이 변환은 인제스션 파이프라인(Task 10)에서 분류 전에 실행됨
- **MUST NOT DO**:
  - 분류 로직은 이 태스크에서 구현하지 않음 (디코딩만)
- **Acceptance Criteria**:
  - [ ] 실제 Uniswap V2 Swap 이벤트 로그 → 구조화된 SwapEvent 파싱 테스트
  - [ ] 실제 ERC-20 Transfer 로그 → TransferEvent 파싱 테스트
  - [ ] 알 수 없는 이벤트 토픽 → null 반환 (에러 아님)

---

### Task 8: 분류 엔진 (Classification Engine)

- **Files**:
  - `backend/src/main/kotlin/com/example/ledger/domain/service/ClassificationService.kt` (신규)
  - `backend/src/main/kotlin/com/example/ledger/domain/classifier/` (신규)
    - `EthTransferClassifier.kt`
    - `Erc20TransferClassifier.kt`
    - `GasFeeClassifier.kt`
    - `UniswapV2Classifier.kt`
    - `UniswapV3Classifier.kt`
    - `UnclassifiedFallback.kt`
  - `backend/src/main/kotlin/com/example/ledger/adapter/persistence/AccountingEventJpaRepository.kt` (신규)
- **MUST DO**:
  - `ClassifierPlugin` 인터페이스 구현체 6개 (FR-6, FR-7)
  - **[CR-005]** `ClassificationService`: **DecodedTransaction** → 모든 플러그인 순회 → matching events 생성 (RawTransaction이 아닌 디코딩된 도메인 모델 사용 — 헥사고날 아키텍처 준수)
  - 하나의 tx에서 복수 이벤트 생성 가능 (e.g., ETH outgoing + gas fee)
  - 각 이벤트에 `classifier_id` 기록 (FR-8)
  - 매칭 안 되면 `UNCLASSIFIED` + `UnclassifiedFallback` (FR-7)
  - 가격(price_krw)은 이 단계에서 채우지 않음 — 별도 enrichment 단계에서 처리
- **MUST NOT DO**:
  - Uniswap 외 DeFi 프로토콜 지원하지 않음
  - 가격 조회를 분류 엔진에서 직접 수행하지 않음
  - **[CR-020]** ERC-721/ERC-1155 토큰은 Task 5에서 수집되지만 MVP에서는 `UNCLASSIFIED`로 분류됨 (NFT 밸류에이션은 스코프 밖). 전용 분류기 불필요
- **Acceptance Criteria**:
  - [ ] ETH incoming → `INCOMING` 이벤트 생성 테스트
  - [ ] ETH outgoing → `OUTGOING` + `FEE` 이벤트 2건 생성 테스트
  - [ ] ERC-20 Transfer → 올바른 토큰 심볼/수량 파싱 테스트
  - [ ] Uniswap V2 Swap → `SWAP` 이벤트 + 입출력 토큰 정보 테스트
  - [ ] Uniswap V3 Swap → `SWAP` 이벤트 테스트
  - [ ] 미지원 컨트랙트 → `UNCLASSIFIED` 테스트
  - [ ] 실패 트랜잭션(status=0) → `FEE`만 생성 테스트

---

### Task 9: 원장 엔진 — 분개 생성 + FIFO + 실현 손익 (통합) [CR-006]

> **[CR-006] 리뷰 반영**: 기존 9a/9b/9c를 단일 원자적 태스크로 통합.
> 분개 생성 시 FIFO 원가와 실현 손익을 한 번에 계산하여 완전한 journal entry를 생성한다.
> 이를 통해 불완전한 중간 상태와 유령 감사 추적 기록을 방지한다.

- **Files**:
  - `backend/src/main/kotlin/com/example/ledger/domain/service/LedgerService.kt` (신규)
  - `backend/src/main/kotlin/com/example/ledger/domain/service/FifoService.kt` (신규)
  - `backend/src/main/kotlin/com/example/ledger/domain/service/GainLossService.kt` (신규)
  - `backend/src/main/kotlin/com/example/ledger/adapter/persistence/JournalJpaRepository.kt` (신규)
  - `backend/src/main/kotlin/com/example/ledger/adapter/persistence/CostBasisLotJpaRepository.kt` (신규)
- **MUST DO**:
  - **분개 생성**: 분류된 AccountingEvent → JournalEntry + JournalLines 생성 (FR-10)
  - 이벤트 유형별 분개 매핑 규칙:
    - `INCOMING` ETH/토큰: `차변 자산:암호화폐:X / 대변 (미지정수입)` + FIFO 로트 생성
    - `OUTGOING` ETH/토큰: `차변 (상대계정) / 대변 자산:암호화폐:X` + FIFO 로트 소비 + 실현 손익
    - `FEE`: `차변 비용:가스비 / 대변 자산:암호화폐:ETH` + FIFO 로트 소비 + 실현 손익
    - `SWAP`: `차변 자산:암호화폐:B / 대변 자산:암호화폐:A + 손익` + 입력 토큰 FIFO 소비 + 출력 토큰 로트 생성
  - **FIFO 원가 추적** (FR-11):
    - 토큰 수신 시 새 CostBasisLot 생성 (acquisition_date, quantity, unit_cost_krw)
    - 토큰 처분 시 FIFO 순서로 로트 소비, 부분 소비 시 remaining_qty 정확히 업데이트
    - 가스비(ETH) 소비도 FIFO 로트에서 차감
    - 로트 부족 시 → unit_cost = 0으로 처리하고 "원가 미확인" 플래그
  - **실현 손익 계산**:
    - 자산 처분 시 실현 손익 = (처분 시 KRW 단가 - FIFO 취득 원가) × 수량
    - 손익 금액을 journal_lines에 `수익:실현이익` 또는 `비용:실현손실`로 기록
  - 불변식 검증: 모든 분개의 차변 합 = 대변 합 (FR-10)
  - **[CR-015]** ERC-20 동적 계정 생성: 처음 등장하는 ERC-20 토큰의 분개 생성 시, `자산:암호화폐:ERC20:{SYMBOL}` 계정이 `accounts` 테이블에 없으면 자동 생성 (`is_system=true`, `category=ASSET`). journal_lines.account_code FK 무결성 보장
  - **[CR-010]** 동일 raw_transaction에서 생성된 모든 journal_entries에 `raw_transaction_id` 설정
  - **[CR-001]** 모든 KRW 금액 중간 계산은 Kotlin `BigDecimal(MathContext.DECIMAL128)` 사용, 최종 저장 시 HALF_UP 라운딩
  - **[CR-002]** `@Transactional(isolation = Isolation.SERIALIZABLE)` 적용
  - KRW 금액은 PricePort에서 조회하여 채움
- **MUST NOT DO**:
  - 미실현 손익(mark-to-market) 계산하지 않음
  - LIFO, 가중평균법 구현하지 않음
- **Acceptance Criteria**:
  - [ ] **[CR-015]** 처음 등장하는 ERC-20 토큰 분개 생성 → accounts 테이블에 자동 계정 생성 확인
  - [ ] INCOMING 이벤트 → 유효한 분개 생성 (차변=대변) + FIFO 로트 생성
  - [ ] OUTGOING 이벤트 → 유효한 분개 생성 + FIFO 로트 소비
  - [ ] FEE 이벤트 → 가스비 분개 생성 + ETH FIFO 로트 소비 + 실현 손익
  - [ ] SWAP → 입력 토큰 FIFO 소비 + 출력 토큰 로트 생성 + 실현 손익
  - [ ] 차변 ≠ 대변일 경우 예외 발생
  - [ ] 3회 매수 후 일부 매도 → 가장 오래된 로트부터 소진 테스트
  - [ ] 부분 소비 → remaining_qty 정확성 테스트
  - [ ] 가스비 ETH → FIFO 로트 차감 + 실현 손익 테스트
  - [ ] 로트 부족 시 예외 없이 0 원가로 처리 테스트
  - [ ] ETH 취득 원가 100만원, 처분 시 150만원 → 실현이익 50만원 테스트
  - [ ] **[CR-001]** 큰 토큰 수량 × 작은 KRW 가격 곱셈 시 정밀도 손실 없음 테스트

---

### Task 10: 파이프라인 오케스트레이션

- **Files**:
  - `backend/src/main/kotlin/com/example/ledger/application/usecase/IngestWalletUseCase.kt` (신규)
  - `backend/src/main/kotlin/com/example/ledger/application/usecase/SyncPipelineUseCase.kt` (신규)
- **MUST DO**:
  - 전체 파이프라인 오케스트레이션: 인제스트 → **ABI 디코딩 [CR-005]** → 분류 → 가격 보강 → 분개 생성(FIFO+손익 포함) [CR-006]
  - `wallets.sync_status` 상태 머신: PENDING → SYNCING → COMPLETED / FAILED
  - 부분 실패 처리: 가격 조회 실패 시 price=0으로 진행, 전체 파이프라인은 중단하지 않음
  - **[CR-014]** 멱등성: 재동기화 시 `wallets.last_synced_block` 이후의 새 트랜잭션만 수집. 기존 Layer 2/3 데이터와 사용자 수정(승인된 분개)은 보존
  - 비동기 실행 (Spring `@Async` 또는 코루틴)
- **MUST NOT DO**:
  - 실시간 스트리밍/WebSocket 구현하지 않음
- **Acceptance Criteria**:
  - [ ] 전체 파이프라인 통합 테스트: 지갑 등록 → 분개 생성까지
  - [ ] 동일 지갑 2회 동기화 → 중복 없음 테스트 (새 트랜잭션만 추가, 기존 승인 분개 보존)
  - [ ] 부분 실패 → 파이프라인 계속 진행 테스트
  - [ ] sync_status 상태 전이 테스트

---

### Task 11: 감사 추적 (Audit Trail)

- **Files**:
  - `backend/src/main/kotlin/com/example/ledger/adapter/persistence/AuditServiceImpl.kt` (신규) [CR-009 — adapter 레이어에 배치, domain에는 인터페이스만]
  - `backend/src/main/kotlin/com/example/ledger/adapter/persistence/AuditLogJpaRepository.kt` (신규)
- **MUST DO**:
  - `AuditService` 구현: JournalEntry/JournalLine/Account 생성/수정/승인 시 audit_log 기록 (FR-15~18)
  - old_value / new_value를 JSONB로 저장
  - audit_log 삭제 불가 (FR-18) — DELETE 쿼리 없음
- **MUST NOT DO**:
  - audit_log에 대한 삭제/수정 API 제공하지 않음
- **Acceptance Criteria**:
  - [ ] 분개 생성 시 audit_log CREATE 기록 확인
  - [ ] 분개 수정 시 old/new value 정확히 기록 확인
  - [ ] 분개 승인 시 APPROVE 액션 기록 확인

---

### Task 12a: REST API — 지갑 + 계정과목

- **Files**:
  - `backend/src/main/kotlin/com/example/ledger/adapter/web/WalletController.kt` (신규)
  - `backend/src/main/kotlin/com/example/ledger/adapter/web/AccountController.kt` (신규)
  - `backend/src/main/kotlin/com/example/ledger/application/dto/` (신규 — Request/Response DTOs)
- **MUST DO**:
  - `POST /api/wallets` — 지갑 등록 + 파이프라인 트리거 (비동기)
  - `GET /api/wallets` — 등록된 지갑 목록
  - `GET /api/wallets/{address}/status` — 동기화 상태
  - `GET /api/accounts` — 계정과목 목록
  - `POST /api/accounts` — 계정과목 추가 (FR-14)
  - `PATCH /api/accounts/{id}` — 계정과목 수정 (**[CR-003]** is_system=true인 경우 `code` 필드 변경 거부, `name` 수정만 허용)
  - `DELETE /api/accounts/{id}` — 계정과목 삭제 (is_system=true인 경우 거부, **[CR-003]** 기존 journal_lines에서 참조 중인 계정 삭제 거부)
  - Ethereum 주소 형식 검증 (0x + 40 hex chars)
- **Acceptance Criteria**:
  - [ ] 유효 주소 POST → 201 + 파이프라인 비동기 시작
  - [ ] 잘못된 주소 → 400 에러
  - [ ] 중복 주소 → 기존 지갑 반환 (409 또는 200)
  - [ ] 계정과목 CRUD 전체 동작 확인
  - [ ] 시스템 계정 삭제 시도 → 400 거부

### Task 12b: REST API — 분개 + 미분류 + 가격

- **Files**:
  - `backend/src/main/kotlin/com/example/ledger/adapter/web/JournalController.kt` (신규)
  - `backend/src/main/kotlin/com/example/ledger/adapter/web/UnclassifiedController.kt` (신규)
  - `backend/src/main/kotlin/com/example/ledger/adapter/web/PriceController.kt` (신규)
- **MUST DO**:
  - `GET /api/journals` — 필터(기간, 상태, 계정과목), 페이지네이션 (FR-19, FR-20)
  - `GET /api/journals/{id}` — 상세 (원본 tx, 분류 규칙, 가격 출처 포함) (FR-21)
  - `PATCH /api/journals/{id}` — 수정 (계정과목, 금액, 메모) + audit_log 기록 (FR-22)
  - `POST /api/journals/{id}/approve` — 승인 (FR-23)
  - `POST /api/journals/bulk-approve` — 일괄 승인
  - `GET /api/unclassified` — 미분류 거래 목록
  - **[CR-012]** `POST /api/unclassified/{id}/classify` — 수동 분류: (1) accounting_event.event_type을 사용자 지정 타입으로 업데이트, classifier_id = "MANUAL" (2) 원장 엔진(Task 9)으로 분개 자동 생성 (3) status = MANUAL_CLASSIFIED로 설정
  - `GET /api/prices/{token}/{date}` — 가격 조회
  - **[CR-004]** 승인된 분개 수정 시도 → JournalEntry.validateEditable() 도메인 검증 → 400 거부 (컨트롤러가 아닌 도메인 레이어에서 검증)
- **Acceptance Criteria**:
  - [ ] 분개 목록 필터 (기간, 상태) 동작 확인
  - [ ] 분개 수정 → audit_log 기록 확인
  - [ ] 승인 후 재수정 시도 → 400
  - [ ] 미분류 수동 분류 → 상태 변경 확인

### Task 12c: REST API — 내보내기

- **Files**:
  - `backend/src/main/kotlin/com/example/ledger/adapter/web/ExportController.kt` (신규)
  - `backend/src/main/kotlin/com/example/ledger/adapter/export/CsvExporter.kt` (신규)
  - `backend/src/main/kotlin/com/example/ledger/adapter/export/ExcelExporter.kt` (신규)
- **MUST DO**:
  - `POST /api/export` — 기간 필터 + 형식(CSV/Excel) 선택 + **[CR-011]** 선택적 `wallet_address` 필터 (FR-24, FR-25, FR-26)
  - CSV: OpenCSV 사용, UTF-8 BOM 포함 (한글 Excel 호환)
  - Excel: Apache POI 사용, .xlsx 형식
  - 컬럼: 날짜, 설명, 차변계정, 대변계정, 차변금액, 대변금액, 토큰, 토큰수량, tx해시, 상태
- **Acceptance Criteria**:
  - [ ] CSV 다운로드 → 파일 내용이 DB 데이터와 일치
  - [ ] Excel 다운로드 → 유효한 .xlsx 파일
  - [ ] 기간 필터 적용 확인
  - [ ] 빈 결과 → 헤더만 있는 파일 반환

---

### Task 13: 프론트엔드 — 지갑 입력 + 동기화 상태

- **Files**:
  - `frontend/src/app/page.tsx` (수정 — 메인 페이지)
  - `frontend/src/app/wallets/page.tsx` (신규)
  - `frontend/src/components/WalletInput.tsx` (신규)
  - `frontend/src/components/SyncStatus.tsx` (신규)
  - `frontend/src/lib/api.ts` (신규 — API 클라이언트)
  - `frontend/src/types/wallet.ts` (신규)
- **MUST DO**:
  - 지갑 주소 입력 폼 (0x... 형식 검증)
  - 등록 버튼 → `POST /api/wallets` 호출
  - 동기화 진행 상태 표시 (폴링으로 `/api/wallets/{address}/status` 조회)
  - 등록된 지갑 목록 표시
  - TanStack Query 설정
- **MUST NOT DO**:
  - 월렛 연결(MetaMask 등) 구현하지 않음 — 주소 텍스트 입력만
- **Acceptance Criteria**:
  - [ ] 유효 주소 입력 → API 호출 → 동기화 시작 확인
  - [ ] 잘못된 주소 → 클라이언트 측 검증 에러
  - [ ] 동기화 진행 중 상태 표시 (SYNCING → COMPLETED)

### Task 14: 프론트엔드 — 분개 목록 + 상세

- **Files**:
  - `frontend/src/app/journals/page.tsx` (신규)
  - `frontend/src/app/journals/[id]/page.tsx` (신규)
  - `frontend/src/components/JournalTable.tsx` (신규)
  - `frontend/src/components/JournalDetail.tsx` (신규)
  - `frontend/src/components/JournalFilters.tsx` (신규)
  - `frontend/src/types/journal.ts` (신규)
- **MUST DO**:
  - TanStack Table로 분개 목록 렌더링 (날짜, 설명, 차변, 대변, 금액, 상태)
  - 필터: 기간(DatePicker), 상태(드롭다운), 계정과목(드롭다운) (FR-20)
  - 상세 페이지: 원본 tx hash (Etherscan 링크), 분류 규칙, 가격 출처, 감사 이력 (FR-21)
  - 상태별 색상 구분 (검토필요=노랑, 승인됨=초록, 미분류=빨강)
  - **[CR-010]** 동일 트랜잭션(raw_transaction_id)에서 생성된 분개를 그룹으로 표시 (접기/펼치기)
- **Acceptance Criteria**:
  - [ ] 분개 목록 테이블 렌더링 확인
  - [ ] 필터 적용 시 API 재호출 + 결과 갱신
  - [ ] 상세 페이지에서 tx hash → Etherscan 링크 동작

### Task 15: 프론트엔드 — 검토/수정/승인

- **Files**:
  - `frontend/src/components/JournalEditForm.tsx` (신규)
  - `frontend/src/components/BulkApproveBar.tsx` (신규)
  - `frontend/src/components/ManualClassifyForm.tsx` (신규)
  - `frontend/src/app/unclassified/page.tsx` (신규)
- **MUST DO**:
  - 분개 수정 폼: 계정과목 선택, 금액 수정, 메모 입력 → `PATCH /api/journals/{id}` (FR-22)
  - 승인 버튼 → `POST /api/journals/{id}/approve` (FR-23)
  - 체크박스 선택 → 일괄 승인 → `POST /api/journals/bulk-approve`
  - 미분류 거래 목록 + 수동 분류 폼 → `POST /api/unclassified/{id}/classify`
  - Optimistic update + 에러 시 롤백 (TanStack Query mutation)
- **Acceptance Criteria**:
  - [ ] 분개 수정 → 저장 → 목록 갱신 확인
  - [ ] 승인 → 상태 변경 → 재수정 불가 UI 반영
  - [ ] 일괄 승인 동작 확인
  - [ ] 미분류 수동 분류 → 분개 목록에 반영

### Task 16: 프론트엔드 — 내보내기

- **Files**:
  - `frontend/src/components/ExportDialog.tsx` (신규)
- **MUST DO**:
  - 내보내기 다이얼로그: 기간 선택 + 형식(CSV/Excel) 선택 (FR-24, FR-25, FR-26)
  - `POST /api/export` 호출 → 파일 다운로드
  - 다운로드 중 로딩 상태 표시
- **Acceptance Criteria**:
  - [ ] CSV 내보내기 → 파일 다운로드 확인
  - [ ] Excel 내보내기 → 파일 다운로드 확인
  - [ ] 기간 필터 적용 확인

---

### Task 17: 통합 테스트 + E2E

- **Files**:
  - `backend/src/test/kotlin/com/example/ledger/integration/` (신규)
    - `PipelineIntegrationTest.kt`
    - `JournalApiIntegrationTest.kt`
    - `ExportIntegrationTest.kt`
- **MUST DO**:
  - 전체 파이프라인 통합 테스트: 지갑 등록 → raw_transactions → accounting_events → journal_entries (mock Alchemy/CoinGecko)
  - 분개 수정 → audit_log 기록 확인
  - 분개 승인 → 이후 수정 불가 확인
  - CSV/Excel 내보내기 → 파일 내용 검증
  - 엣지 케이스: 빈 지갑, 중복 등록, 잘못된 주소, self-transfer
  - **[CR-002]** 동시 FIFO 로트 소비 레이스 컨디션 테스트: 2개 스레드가 동일 ETH 로트를 동시에 소비 시도 → SERIALIZABLE 격리로 충돌 방지 확인
  - **[CR-004]** bulk-approve 후 개별 분개 수정 시도 → 400 거부 테스트
  - **[CR-007]** NFR-1 성능 테스트: 1,000건 트랜잭션 지갑 전체 파이프라인 5분 이내 완료 확인
- **[CR-013]** 참고: 프론트엔드 E2E 테스트는 MVP에서 수동 검증으로 대체. Playwright 자동화는 post-MVP에서 추가.
- **Acceptance Criteria**:
  - [ ] 모든 통합 테스트 통과
  - [ ] 테스트 커버리지: 핵심 도메인 서비스 80% 이상

---

## Dependencies

```
Task 1 (스캐폴딩)
  ├── Task 2a~2e (DB 마이그레이션) ───────────────────────┐
  │                                                       │
  └── Task 3 (도메인 모델 + 포트) ─────────────────────────┤
        │                                                 │
        ├── Task 4 (복원력 패턴 + SERIALIZABLE)             │
        │     ├── Task 5 (Alchemy 어댑터) ──── [2b 필요]   │
        │     └── Task 6 (CoinGecko 어댑터) ── [2a 필요]   │
        │                                                 │
        ├── Task 7 (ABI 디코딩/web3j)                      │
        │     └── Task 8 (분류 엔진) ──────── [2c, 7 필요] │
        │                                                 │
        └── Task 9 (원장+FIFO+손익 통합) ─ [2d, 8, 6 필요] │
                    │                                      │
                    └── Task 10 (파이프라인 오케스트레이션)    │
                          │                                │
                    Task 11 (감사 추적) ──── [2a 필요]      │
                          │                                │
              ┌───────────┼───────────────────────────┐    │
        Task 12a     Task 12b                   Task 12c   │
        (API 지갑+계정) (API 분개)              (API 내보내기)│
              │           │                           │    │
              └───────────┼───────────────────────────┘    │
                          │                                │
              ┌───────────┼───────────────────────────┐    │
        Task 13      Task 14      Task 15       Task 16    │
        (FE 지갑)    (FE 분개목록)  (FE 수정/승인) (FE 내보내기)
              │           │           │           │        │
              └───────────┴───────────┴───────────┘        │
                          │                                │
                    Task 17 (통합 테스트) ──────────────────┘
```

**병렬 가능 그룹**:

| Phase | 병렬 실행 가능 태스크 | 선행 조건 |
|-------|---------------------|----------|
| A | Task 2a~2e (DB 마이그레이션 5건) | Task 1 |
| B | Task 4, Task 7 | Task 3 |
| C | Task 5, Task 6 | Task 4, Task 2a/2b |
| D | Task 8 | Task 7, Task 5 완료 |
| E | Task 9 (원장+FIFO+손익 통합) | Task 8, Task 6, Task 2d |
| F | Task 12a, Task 12b, Task 12c | Task 10, 11 |
| G | Task 13, Task 14, Task 15, Task 16 | Task 12a~c |

## Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|-----------|------------|
| Uniswap V3 이벤트 디코딩 복잡도 과소평가 | High | Medium | Task 7에서 별도 격리, 실패 시 V3는 UNCLASSIFIED 처리 |
| CoinGecko 레이트 리밋으로 NFR-1(5분) 위반 | Medium | High | 가격 배치 조회 + 캐시 적극 활용, 첫 동기화는 5분 초과 허용 |
| FIFO 로트 추적 엣지 케이스 (로트 부족, 크로스체인 전입) | High | Medium | 로트 부족 시 0 원가 처리 + 플래그, 스코프 밖 유입은 수동 원가 입력 |
| Alchemy getAssetTransfers에 실패 tx 미포함 | Medium | Medium | eth_getTransactionReceipt 보조 호출로 보완 |
| 회계 분개 매핑 규칙 정확성 (도메인 전문성 부족) | High | Medium | 기본 규칙 구현 후 회계사 피드백으로 반복 수정 |
| Kotlin + Spring Boot + Next.js 모노레포 설정 복잡도 | Low | Medium | Gradle + npm 독립 빌드, Docker Compose로 통합 |

## Verification

전체 구현 완료 후 검증 체크리스트:

- [ ] 모든 단위 테스트 통과 (`./gradlew test`)
- [ ] 모든 통합 테스트 통과
- [ ] Docker Compose로 전체 스택 기동 확인
- [ ] 실제 Ethereum 지갑 주소 1개로 E2E 테스트:
  - [ ] 지갑 등록 → 동기화 완료
  - [ ] 분개 목록 표시 (ETH 전송, ERC-20 전송, 가스비 확인)
  - [ ] 미분류 거래 존재 시 수동 분류
  - [ ] 분개 수정 → 감사 이력 확인
  - [ ] 분개 승인
  - [ ] CSV/Excel 내보내기 → 파일 열기 확인
- [ ] 1,000건 트랜잭션 지갑으로 성능 테스트 (NFR-1)
- [ ] 모든 금액의 차변 합 = 대변 합 (전체 원장 정합성)
