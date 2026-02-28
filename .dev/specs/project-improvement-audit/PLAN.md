# Backend Stability Hardening: Error Propagation & Concurrency Safety

> 백엔드의 에러 처리 결함(silent failure)과 동시성 레이스 컨디션을 수정하여 서비스 안정성을 확보한다.

---

## Verification Summary

### Agent-Verifiable (A-items)
| ID | Criterion | Method | Related TODO |
|----|-----------|--------|-------------|
| A-1 | EthereumRpcClient RPC 에러 시 EthereumRpcException throw | unit test: `*EthereumRpcClientTest*` | TODO 1 |
| A-2 | getBlockByNumber/getTransactionReceipt null result 시 기존 동작 유지 | unit test: `*EthereumRpcClientTest*` | TODO 1 |
| A-3 | AdaptiveRangeFetchTest 회귀 없음 | `./gradlew test --tests "*AdaptiveRangeFetchTest*"` | TODO 1 |
| A-4 | EthereumRpcClientBufferLimitTest 회귀 없음 | `./gradlew test --tests "*EthereumRpcClientBufferLimitTest*"` | TODO 1 |
| A-5 | 동시 sync 요청 시 두 번째 호출 skip 확인 | integration test: `*SyncConcurrencyIntegrationTest*` | TODO 2 |
| A-6 | PipelineIntegrationTest 회귀 없음 | `./gradlew test --tests "*PipelineIntegrationTest*"` | TODO 2 |
| A-7 | 동일 주소 동시 등록 시 중복 없이 1회 저장 | integration test: `*WalletIngestRaceIntegrationTest*` | TODO 3 |
| A-8 | WalletApiIntegrationTest 회귀 없음 | `./gradlew test --tests "*WalletApiIntegrationTest*"` | TODO 3 |
| A-9 | @Retryable serialization 재시도 후 성공 | integration test: `*SerializationRetryIntegrationTest*` | TODO 4 |
| A-10 | FifoConcurrencyIntegrationTest 통과 (errors=0 기대) | `./gradlew test --tests "*FifoConcurrencyIntegrationTest*"` | TODO 4 |
| A-11 | ensureAccountExists 동시 호출 시 중복 없음 | integration test: `*AccountRaceIntegrationTest*` | TODO 4 |
| A-12 | CoinGecko 429 시 RetryExecutor 재시도 동작 | unit test: `*CoinGeckoErrorHandlingTest*` | TODO 5 |
| A-13 | CoinGecko 5xx 시 sync 계속 진행 (가격 누락 허용) | unit test: `*CoinGeckoErrorHandlingTest*` | TODO 5 |
| A-14 | 미핸들 예외 → 안전한 JSON 응답 (500, 스택트레이스 미노출) | integration test: `*ApiExceptionHandlerIntegrationTest*` | TODO 6 |
| A-15 | RPC 장애 시 wallet 상태 FAILED 전환 | integration test: `*FailureScenarioIntegrationTest*` | TODO 7 |
| A-16 | 전체 테스트 스위트 green | `task verify` | TODO Final |

### Human-Required (H-items)
| ID | Criterion | Reason | Review Material |
|----|-----------|--------|----------------|
| H-1 | RPC error vs null result 구분 정책 적절성 | 비즈니스 판단 (pending tx skip vs sync 중단) | EthereumRpcClient 변경 diff |
| H-2 | Stale SYNCING 복구 정책 필요 여부 | crash 시 SYNCING 상태 잔류 가능 | SyncPipelineUseCase 변경 diff |
| H-3 | CoinGecko 장애 시 가격 누락 분개 허용 여부 | 회계 정확성 정책 | CoinGeckoAdapter 변경 diff |
| H-4 | 실제 Ethereum RPC 엔드포인트 연동 테스트 | 외부 API 키 필요 | 수동 E2E 실행 |
| H-5 | TransactionConfig JpaTransactionManager 커스터마이징 적절성 | Spring 내부 동작 이해 필요 | TransactionConfig.kt 변경 diff |

### Sandbox Agent Testing (S-items)
없음 — sandbox 인프라 미구축 상태

### Verification Gaps
- 실제 Ethereum RPC / CoinGecko API 통합 테스트 불가 (외부 API 키 필요 → H-4로 대체)
- @Async 경계에서의 예외 전파는 Spring 프레임워크 동작에 의존 → 통합 테스트로 간접 검증

---

## External Dependencies Strategy

### Pre-work (user prepares before AI work)
| Dependency | Action | Command/Step | Blocking? |
|------------|--------|-------------|-----------|
| Docker Desktop | Testcontainers 실행을 위해 Docker 가동 확인 | `docker info` | Yes |

### During (AI work strategy)
| Dependency | Dev Strategy | Rationale |
|------------|-------------|-----------|
| PostgreSQL | Testcontainers `postgres:16-alpine` (IntegrationTestBase) | 기존 패턴 재사용 |
| Ethereum RPC | `@MockBean BlockchainDataPort` 또는 로컬 HttpServer mock | PipelineIntegrationTest 기존 패턴 |
| CoinGecko API | `@MockBean PricePort` 또는 WebClientResponseException 직접 생성 | RetryExecutorTest 기존 패턴 |

### Post-work (user actions after completion)
| Task | Action | Command/Step |
|------|--------|-------------|
| 실제 RPC 연동 확인 | `.env`에 실제 ETHEREUM_RPC_URL 설정 후 wallet 등록/sync 확인 | `task dev` → wallet 등록 |
| Stale SYNCING 정책 검토 | crash 후 SYNCING 잔류 시 복구 방법 결정 | H-2 참조 |

---

## Context

### Original Request
프로젝트 보완점 식별 → 안정성 우선 (에러 처리 + 동시성) 개선 계획 수립

### Interview Summary
**Key Discussions**:
- 우선 영역: 안정성 (에러 처리 + 동시성) 선택
- post-MVP hardening plan과 별도 진행 (중복 없음)
- 인증/인가: 현재 불필요 (MVP out-of-scope 유지)
- Sync guard: DB-level guard (UPDATE WHERE sync_status<>'SYNCING') 선택
- FIFO retry: @Retryable 어노테이션 (Spring AOP, @SerializableTx와 동일 메서드) 선택
- ensureAccountExists race: INSERT ON CONFLICT DO NOTHING으로 이번 스코프에 포함

**Research Findings**:
- Spring @Retryable order = LOWEST_PRECEDENCE - 1 → @Transactional 바깥에서 실행 보장
- PostgreSQL 40001 commit-time 예외가 JpaSystemException으로 래핑됨 → custom JpaTransactionManager 필요
- EthereumRpcClient: RPC error field → throw, null result → 유지 (pending tx 등 정상 케이스)
- DataIntegrityViolationException catch 패턴보다 INSERT ON CONFLICT가 더 안전 (트랜잭션 poisoning 방지)

---

## Work Objectives

### Core Objective
EthereumRpcClient의 silent failure를 예외 전파로 전환하고, 동시성 레이스 컨디션(sync, wallet 등록, FIFO, 계정 생성)을 해결하여 장애 상황에서도 데이터 정합성을 보장한다.

### Concrete Deliverables
- EthereumRpcClient: RPC error 시 EthereumRpcException throw (getBlockNumber, getBlockByNumber, getTransactionReceipt, getTransactionByHash)
- SyncPipelineUseCase: DB-level sync guard (trySetSyncing)
- IngestWalletUseCase: DataIntegrityViolationException catch로 race condition 해결
- LedgerService: @Retryable + custom JpaTransactionManager로 serialization retry
- LedgerService: ensureAccountExists → INSERT ON CONFLICT DO NOTHING
- CoinGeckoAdapter: RetryExecutor 주입으로 에러 재시도 + 장애 시 graceful degradation
- ApiExceptionHandler: 502/503/409 + fallback 핸들러 추가
- 장애 시나리오 통합 테스트 7개 이상

### Definition of Done
- [ ] RPC 장애 시 wallet 상태 FAILED 전환 + 에러 로그 기록
- [ ] 동시 sync 요청 시 하나만 실행, 나머지 skip
- [ ] FIFO 동시 소비 시 serialization retry로 정확한 잔량 유지
- [ ] 동시 wallet 등록 시 중복 없이 1회 저장
- [ ] CoinGecko 장애 시 sync 계속 진행 (가격 누락 분개 허용)
- [ ] 미핸들 예외 → 안전한 JSON 에러 응답 (스택트레이스 미노출)
- [ ] `task verify` 전체 통과

### Must NOT Do (Guardrails)
- `@SerializableTx` 어노테이션 정의(TransactionConfig.kt) 변경 금지
- `SyncPipelineUseCase.sync()`에 `@Transactional` 추가 금지 (장시간 트랜잭션 방지)
- `FifoConcurrencyIntegrationTest` assertion을 약화시키지 않고, 더 강한 assertion으로 교체
- `WalletRepository` 포트에 persistence 개념(pessimistic lock, FOR UPDATE 등) 유출 금지
- `syncAsync` 반환 타입을 `CompletableFuture`로 변경 금지
- CoinGecko retry가 RateLimiter를 우회하지 않도록 주의
- `@Retryable` 적용 시 `PROPAGATION_REQUIRES_NEW` 사용 금지 (원자성 유지)
- 기존 API 응답 계약(JSON 구조) 변경 금지
- 인증/인가 추가 금지 (MVP out-of-scope)
- 프론트엔드 코드 변경 금지

---

## Task Flow

```
TODO-1 (RPC error propagation)
    ↓
TODO-2 (Sync guard) ←── depends on TODO-1
    ↓
TODO-3 (Wallet race)  ─── parallel with TODO-2
    ↓
TODO-4 (FIFO retry + ensureAccountExists) ←── depends on TODO-1
    ↓
TODO-5 (CoinGecko error) ─── parallel with TODO-4
    ↓
TODO-6 (ApiExceptionHandler) ←── depends on TODO-1~5
    ↓
TODO-7 (Failure scenario tests) ←── depends on TODO-1~6
    ↓
TODO-Final (Verification)
```

---

## Dependency Graph

| TODO | Requires (Inputs) | Produces (Outputs) | Type |
|------|-------------------|-------------------|------|
| 1 | - | `rpc_client_path` (file), `rpc_exception_behavior` (string) | work |
| 2 | `todo-1.rpc_exception_behavior` | `sync_guard_path` (file) | work |
| 3 | - | `wallet_ingest_path` (file) | work |
| 4 | `todo-1.rpc_exception_behavior` | `retry_config_path` (file), `ledger_service_path` (file) | work |
| 5 | - | `coingecko_adapter_path` (file) | work |
| 6 | `todo-1~5 completed` | `exception_handler_path` (file) | work |
| 7 | `todo-1~6 completed` | `test_files` (list) | work |
| Final | all outputs | - | verification |

---

## Parallelization

| Group | TODOs | Reason |
|-------|-------|--------|
| A | TODO-2, TODO-3 | 독립 모듈 (SyncPipeline vs IngestWallet) |
| B | TODO-4, TODO-5 | 독립 모듈 (LedgerService vs CoinGeckoAdapter) |

---

## Commit Strategy

| After TODO | Message | Files | Condition |
|------------|---------|-------|-----------|
| 1 | `fix: propagate RPC errors instead of silent failure` | `EthereumRpcClient.kt` | always |
| 2 | `fix: prevent concurrent wallet sync with DB-level guard` | `SyncPipelineUseCase.kt`, `WalletRepository.kt`, `WalletJpaRepository.kt`, `SpringDataWalletRepository.kt` | always |
| 3 | `fix: handle concurrent wallet registration race condition` | `IngestWalletUseCase.kt` | always |
| 4 | `fix: add serialization retry and account upsert for concurrency safety` | `TransactionConfig.kt`, `LedgerService.kt`, `LedgerApplication.kt`, `AccountJpaRepository.kt`, `SpringDataAccountRepository.kt` | always |
| 5 | `fix: add retry and error classification to CoinGecko adapter` | `CoinGeckoAdapter.kt`, `CoinGeckoClient.kt` | always |
| 6 | `fix: expand API exception handler for comprehensive error responses` | `ApiExceptionHandler.kt` | always |
| 7 | `test: add failure scenario integration tests` | `*IntegrationTest.kt` (new files) | always |

---

## Error Handling

### Failure Categories

| Category | Examples | Detection Pattern |
|----------|----------|-------------------|
| `env_error` | Docker not running, DB connection refused | `/ECONNREFUSED\|timeout\|Cannot connect/i` |
| `code_error` | Type error, compile failure, test failure | `/error:\|FAILED\|BUILD FAILED/i` |
| `scope_internal` | Missing prerequisite file, schema mismatch | Verify Worker `suggested_adaptation` present |
| `unknown` | Unclassifiable errors | Default fallback |

### Failure Handling Flow

| Scenario | Action |
|----------|--------|
| work fails | Retry up to 2 times → Analyze → (see below) |
| verification fails | Analyze immediately (no retry) → (see below) |
| Worker times out | Halt and report |
| Missing Input | Skip dependent TODOs, halt |

### After Analyze

| Category | Action |
|----------|--------|
| `env_error` | Halt + log to `issues.md` |
| `code_error` | Create Fix Task (depth=1 limit) |
| `scope_internal` | Adapt → Dynamic TODO (depth=1) |
| `unknown` | Halt + log to `issues.md` |

### Fix Task Rules
- Fix Task type is always `work`
- Fix Task failure → Halt (no further Fix Task creation)
- Max depth = 1

---

## Runtime Contract

| Aspect | Specification |
|--------|---------------|
| Working Directory | Repository root (`/Users/mildsalmon/source_code/blockchain-double-entry-bookkeeping`) |
| Network Access | Allowed (for test dependencies) |
| Package Install | Denied (use existing deps only — spring-retry already in build.gradle.kts) |
| File Access | Repository only |
| Max Execution Time | 5 minutes per TODO |
| Git Operations | Denied (Orchestrator handles) |

---

## TODOs

### [x] TODO 1: Fix EthereumRpcClient silent failure — propagate RPC errors as exceptions

**Type**: work

**Required Tools**: (none)

**Inputs**: (none — first task)

**Outputs**:
- `rpc_client_path` (file): `backend/src/main/kotlin/com/example/ledger/adapter/ethereum/EthereumRpcClient.kt`
- `rpc_exception_behavior` (string): "RPC error field → throw EthereumRpcException; null result → return null (unchanged)"

**Steps**:
- [ ] Read `EthereumRpcClient.kt` — identify all methods that silently return 0L/null on RPC error
- [ ] Modify `getBlockNumber()`: replace `return 0L` on `response.error` with `throw EthereumRpcException(it.code, it.message)`; also throw if `response.result` is null (block number should never be null)
- [ ] Modify `getBlockByNumber()`: replace `return null` on `response.error` with `throw EthereumRpcException`; keep `return null` for null `response.result` (block may not exist yet)
- [ ] Modify `getTransactionReceipt()`: same pattern — throw on `response.error`, keep null for null result (pending tx)
- [ ] Modify `getTransactionByHash()`: same pattern — throw on `response.error`, keep null for null result
- [ ] Verify `getLogs()` already throws correctly (no change needed)
- [ ] Write unit test `EthereumRpcClientErrorPropagationTest`: verify each method throws `EthereumRpcException` when RPC returns error field, and returns null when result is null (except getBlockNumber which also throws on null result)

**Must NOT do**:
- Do not change method return types (keep `Long`, `BlockResponse?`, etc.)
- Do not change the `EthereumRpcException` class definition
- Do not modify `EthereumRpcAdapter` (callers already handle exceptions via RetryExecutor)
- Do not run git commands

**References**:
- `adapter/ethereum/EthereumRpcClient.kt:59-60` — getBlockByNumber silent null
- `adapter/ethereum/EthereumRpcClient.kt:73-74` — getTransactionReceipt silent null
- `adapter/ethereum/EthereumRpcClient.kt:88-89` — getTransactionByHash silent null
- `adapter/ethereum/EthereumRpcClient.kt:95-107` — getBlockNumber returns 0L
- `adapter/ethereum/EthereumRpcClient.kt:40-50` — getLogs already throws (reference pattern)
- `adapter/ethereum/dto/RpcResponse.kt` — RpcResponse structure

**Acceptance Criteria**:

*Functional:*
- [ ] `getBlockNumber()` throws `EthereumRpcException` when RPC response has error field
- [ ] `getBlockNumber()` throws `EthereumRpcException` when RPC response result is null
- [ ] `getBlockByNumber()` throws on error field, returns null on null result
- [ ] `getTransactionReceipt()` throws on error field, returns null on null result
- [ ] `getTransactionByHash()` throws on error field, returns null on null result

*Static:*
- [ ] `cd backend && ./gradlew compileKotlin` → exit 0

*Runtime:*
- [ ] `cd backend && ./gradlew test --tests "*EthereumRpcClientErrorPropagationTest*"` → passes
- [ ] `cd backend && ./gradlew test --tests "*AdaptiveRangeFetchTest*"` → passes (regression)
- [ ] `cd backend && ./gradlew test --tests "*EthereumRpcClientBufferLimitTest*"` → passes (regression)
- [ ] `cd backend && ./gradlew test --tests "*EthereumRpcAdapterLogFilterTest*"` → passes (regression)
- [ ] `cd backend && ./gradlew test --tests "*EthereumRpcAdapterTopicQueryTest*"` → passes (regression)

**Verify**:
```yaml
acceptance:
  - given: ["EthereumRpcClient configured with mock WebClient"]
    when: "RPC response contains error field {code: -32000, message: 'server error'}"
    then: ["EthereumRpcException is thrown with code -32000"]
  - given: ["EthereumRpcClient configured with mock WebClient"]
    when: "getBlockByNumber called and RPC result is null"
    then: ["Method returns null (not exception)"]
commands:
  - run: "cd backend && ./gradlew test --tests '*EthereumRpcClient*'"
    expect: "exit 0"
risk: MEDIUM
```

---

### [x] TODO 2: Prevent concurrent wallet sync with DB-level guard

**Type**: work

**Required Tools**: (none)

**Inputs**:
- `rpc_exception_behavior` (string): `${todo-1.outputs.rpc_exception_behavior}` — RPC errors now throw exceptions

**Outputs**:
- `sync_guard_path` (file): `backend/src/main/kotlin/com/example/ledger/application/usecase/SyncPipelineUseCase.kt`

**Steps**:
- [ ] Add `trySetSyncing(address: String): Boolean` method to `WalletRepository` port interface
- [ ] Implement in `WalletJpaRepository`: use `@Modifying @Query("UPDATE WalletEntity w SET w.syncStatus = 'SYNCING', w.updatedAt = CURRENT_TIMESTAMP WHERE w.address = :address AND w.syncStatus <> 'SYNCING'")` → check `Int` return value > 0
- [ ] Add corresponding method to `SpringDataWalletRepository` (Spring Data JPA interface)
- [ ] Modify `SyncPipelineUseCase.sync()`: replace the existing `wallet.syncStatus = SyncStatus.SYNCING` + `walletRepository.save(wallet)` with `if (!walletRepository.trySetSyncing(walletAddress)) { log.info("Already syncing"); return }`
- [ ] Write integration test `SyncConcurrencyIntegrationTest`: launch two concurrent sync calls for same wallet, verify only one executes and the other returns without error
- [ ] Ensure existing `SyncPipelineUseCase.sync()` error handler still sets wallet to FAILED on exception

**Must NOT do**:
- Do not add `@Transactional` to `SyncPipelineUseCase.sync()` method
- Do not use in-memory locks (ConcurrentHashMap)
- Do not add locking concepts to WalletRepository port (trySetSyncing is a state transition, not a lock)
- Do not change the `syncAsync` method signature or return type
- Do not run git commands

**References**:
- `application/usecase/SyncPipelineUseCase.kt:32-84` — syncAsync/sync methods
- `domain/port/WalletRepository.kt` — port interface
- `adapter/persistence/WalletJpaRepository.kt` — JPA adapter
- `adapter/persistence/spring/SpringDataWalletRepository.kt` — Spring Data interface
- `domain/model/SyncStatus.kt` — sync status enum

**Acceptance Criteria**:

*Functional:*
- [ ] `trySetSyncing()` returns true when wallet is not SYNCING
- [ ] `trySetSyncing()` returns false when wallet is already SYNCING
- [ ] Second concurrent sync call for same wallet is silently skipped (no error)
- [ ] Wallet transitions to FAILED on RPC exception during sync

*Static:*
- [ ] `cd backend && ./gradlew compileKotlin` → exit 0

*Runtime:*
- [ ] `cd backend && ./gradlew test --tests "*SyncConcurrencyIntegrationTest*"` → passes
- [ ] `cd backend && ./gradlew test --tests "*PipelineIntegrationTest*"` → passes (regression)

**Verify**:
```yaml
acceptance:
  - given: ["Wallet exists with sync_status=COMPLETED"]
    when: "Two threads call syncAsync(sameAddress) simultaneously"
    then: ["Exactly one thread executes sync", "Other thread returns without error"]
  - given: ["Wallet sync_status=SYNCING (set by trySetSyncing)"]
    when: "RPC throws EthereumRpcException during sync"
    then: ["Wallet sync_status transitions to FAILED"]
commands:
  - run: "cd backend && ./gradlew test --tests '*SyncConcurrency*'"
    expect: "exit 0"
  - run: "cd backend && ./gradlew test --tests '*PipelineIntegrationTest*'"
    expect: "exit 0"
risk: HIGH
```

---

### [x] TODO 3: Handle concurrent wallet registration race condition

**Type**: work

**Required Tools**: (none)

**Inputs**: (none — independent of TODO 1,2)

**Outputs**:
- `wallet_ingest_path` (file): `backend/src/main/kotlin/com/example/ledger/application/usecase/IngestWalletUseCase.kt`

**Steps**:
- [ ] Read `IngestWalletUseCase.registerWallet()` — identify the check-then-act race between `findByAddress` and `save`
- [ ] Wrap `walletRepository.save(...)` in try-catch for `DataIntegrityViolationException` (from `org.springframework.dao`)
- [ ] On catch: re-fetch with `walletRepository.findByAddress(address)!!` and treat as existing wallet
- [ ] Ensure `syncPipelineUseCase.syncAsync(address)` is still called in both paths (new and existing)
- [ ] Write integration test `WalletIngestRaceIntegrationTest`: launch two concurrent `registerWallet` calls with same address, verify exactly one wallet row exists in DB

**Must NOT do**:
- Do not change `WalletRepository` port interface
- Do not add `SELECT FOR UPDATE` or pessimistic locking
- Do not change the API response contract
- Do not run git commands

**References**:
- `application/usecase/IngestWalletUseCase.kt:17-37` — check-then-act race
- `adapter/persistence/entity/WalletEntity.kt` — entity definition
- `backend/src/main/resources/db/migration/V1__core_tables.sql` — `UNIQUE(address)` constraint

**Acceptance Criteria**:

*Functional:*
- [ ] Concurrent registration of same address results in exactly 1 wallet row
- [ ] Both paths (new + existing) trigger syncAsync

*Static:*
- [ ] `cd backend && ./gradlew compileKotlin` → exit 0

*Runtime:*
- [ ] `cd backend && ./gradlew test --tests "*WalletIngestRaceIntegrationTest*"` → passes
- [ ] `cd backend && ./gradlew test --tests "*WalletApiIntegrationTest*"` → passes (regression)

**Verify**:
```yaml
acceptance:
  - given: ["No wallet with address 0xABC exists"]
    when: "Two threads call registerWallet('0xABC') simultaneously"
    then: ["Exactly 1 wallet row exists in DB", "Both calls return successfully"]
commands:
  - run: "cd backend && ./gradlew test --tests '*WalletIngest*'"
    expect: "exit 0"
risk: MEDIUM
```

---

### [x] TODO 4: Add serialization retry and account upsert for concurrency safety

**Type**: work

**Required Tools**: (none)

**Inputs**:
- `rpc_exception_behavior` (string): `${todo-1.outputs.rpc_exception_behavior}` — RPC errors now throw

**Outputs**:
- `retry_config_path` (file): `backend/src/main/kotlin/com/example/ledger/config/TransactionConfig.kt`
- `ledger_service_path` (file): `backend/src/main/kotlin/com/example/ledger/domain/service/LedgerService.kt`

**Steps**:
- [ ] Add `@EnableRetry` to `LedgerApplication.kt`
- [ ] Modify `TransactionConfig.kt`: add custom `JpaTransactionManager` bean that catches `JpaSystemException` on commit, walks cause chain for `SQLException(sqlState=40001)`, and rethrows as `CannotSerializeTransactionException`
- [ ] Add `@Retryable(retryFor = [CannotSerializeTransactionException::class, CannotAcquireLockException::class], maxAttempts = 5, backoff = @Backoff(delay = 50, multiplier = 2.0, maxDelay = 1000))` to `LedgerService.generateEntries()` (same method as `@SerializableTx`)
- [ ] Add `@Recover` method to `LedgerService` for exhausted retries — log error and rethrow
- [ ] Modify `AccountJpaRepository`: add `insertIfAbsent(code, name, category, isSystem)` using native query `INSERT INTO accounts ... ON CONFLICT (code) DO NOTHING`
- [ ] Add `insertIfAbsent` method to `AccountRepository` port interface
- [ ] Modify `LedgerService.ensureAccountExists()`: replace `findByCode + save` with `insertIfAbsent` followed by `findByCode` (to get the ID)
- [ ] Update `FifoConcurrencyIntegrationTest`: change `assertTrue(errors.size <= 1)` to `assertTrue(errors.isEmpty())` — with retry, serialization errors should be auto-resolved
- [ ] Write integration test `SerializationRetryIntegrationTest`: verify that @Retryable successfully retries on serialization failure
- [ ] Write integration test `AccountRaceIntegrationTest`: verify concurrent ensureAccountExists for same code results in exactly 1 account row

**Must NOT do**:
- Do not change the `@SerializableTx` annotation definition
- Do not add `PROPAGATION_REQUIRES_NEW` to any method
- Do not add `@Transactional` to `SyncPipelineUseCase.sync()`
- Do not change the existing `@SerializableTx` on `generateEntries()` — add `@Retryable` alongside it
- Do not run git commands

**References**:
- `config/TransactionConfig.kt` — @SerializableTx definition
- `domain/service/LedgerService.kt:39` — generateEntries with @SerializableTx
- `domain/service/LedgerService.kt:279-291` — ensureAccountExists check-then-act
- `domain/service/FifoService.kt:44` — @SerializableTx on consume
- `domain/port/AccountRepository.kt` — port interface
- `adapter/persistence/AccountJpaRepository.kt` — JPA adapter
- `adapter/persistence/spring/SpringDataAccountRepository.kt` — Spring Data interface
- `integration/FifoConcurrencyIntegrationTest.kt:68` — errors.size <= 1 assertion
- `LedgerApplication.kt` — application class

**Acceptance Criteria**:

*Functional:*
- [ ] `@EnableRetry` is configured on application class
- [ ] Custom JpaTransactionManager correctly translates SQLSTATE 40001 to CannotSerializeTransactionException
- [ ] `generateEntries()` has both `@Retryable` and `@SerializableTx` annotations
- [ ] `ensureAccountExists()` uses INSERT ON CONFLICT DO NOTHING
- [ ] Concurrent account creation for same code results in exactly 1 row

*Static:*
- [ ] `cd backend && ./gradlew compileKotlin` → exit 0

*Runtime:*
- [ ] `cd backend && ./gradlew test --tests "*FifoConcurrencyIntegrationTest*"` → passes with errors.isEmpty()
- [ ] `cd backend && ./gradlew test --tests "*SerializationRetryIntegrationTest*"` → passes
- [ ] `cd backend && ./gradlew test --tests "*AccountRaceIntegrationTest*"` → passes

**Verify**:
```yaml
acceptance:
  - given: ["Two concurrent threads consuming same FIFO lot"]
    when: "Serialization failure occurs on first attempt"
    then: ["@Retryable retries successfully", "Final lot quantities are correct"]
  - given: ["Two threads calling ensureAccountExists('자산:암호화폐:USDC')"]
    when: "Both execute INSERT simultaneously"
    then: ["Exactly 1 account row exists", "Both threads continue without error"]
commands:
  - run: "cd backend && ./gradlew test --tests '*FifoConcurrency*'"
    expect: "exit 0"
  - run: "cd backend && ./gradlew test --tests '*SerializationRetry*'"
    expect: "exit 0"
  - run: "cd backend && ./gradlew test --tests '*AccountRace*'"
    expect: "exit 0"
risk: MEDIUM
```

---

### [x] TODO 5: Add retry and error classification to CoinGecko adapter

**Type**: work

**Required Tools**: (none)

**Inputs**: (none — independent)

**Outputs**:
- `coingecko_adapter_path` (file): `backend/src/main/kotlin/com/example/ledger/adapter/coingecko/CoinGeckoAdapter.kt`

**Steps**:
- [ ] Inject `RetryExecutor` into `CoinGeckoAdapter` constructor
- [ ] Wrap `coinGeckoClient.fetchRangePrices(...)` call in `retryExecutor.execute { ... }` — this handles 429 with Retry-After automatically
- [ ] Add try-catch around the retryExecutor call for `WebClientResponseException`:
  - 4xx (non-429): log warning + return emptyMap (unknown token, etc.)
  - 5xx after retries exhausted: log error + return emptyMap (degraded mode, sync continues)
- [ ] Ensure `coinGeckoRateLimiter.acquire()` is called BEFORE the retryExecutor block (not inside it)
- [ ] Write unit test `CoinGeckoErrorHandlingTest`: verify 429 retry, 404 graceful handling, 500 degraded mode

**Must NOT do**:
- Do not create new exception types
- Do not modify `CoinGeckoClient.kt` (the client returns raw data; error handling is adapter's responsibility)
- Do not bypass `RateLimiter.acquire()` during retries
- Do not run git commands

**References**:
- `adapter/coingecko/CoinGeckoAdapter.kt:51-55` — current fetchRangePrices call
- `adapter/coingecko/CoinGeckoClient.kt:30-55` — client implementation
- `adapter/common/RetryExecutor.kt:6-46` — retry with 429 handling
- `adapter/common/RateLimiter.kt:5-38` — rate limiter
- `adapter/common/RetryConfig.kt` — retry configuration

**Acceptance Criteria**:

*Functional:*
- [ ] CoinGecko 429 response triggers retry with backoff
- [ ] CoinGecko 404 response → returns emptyMap (no crash)
- [ ] CoinGecko 500 response after retries → returns emptyMap (sync continues)
- [ ] RateLimiter.acquire() called before each retry attempt's initial call

*Static:*
- [ ] `cd backend && ./gradlew compileKotlin` → exit 0

*Runtime:*
- [ ] `cd backend && ./gradlew test --tests "*CoinGeckoErrorHandlingTest*"` → passes

**Verify**:
```yaml
acceptance:
  - given: ["CoinGecko API returns 429 with Retry-After: 2"]
    when: "fetchRangePrices is called via RetryExecutor"
    then: ["RetryExecutor waits 2 seconds and retries"]
  - given: ["CoinGecko API returns 500 consistently"]
    when: "All retries exhausted"
    then: ["emptyMap returned", "Sync pipeline continues for remaining transactions"]
commands:
  - run: "cd backend && ./gradlew test --tests '*CoinGeckoError*'"
    expect: "exit 0"
risk: LOW
```

---

### [x] TODO 6: Expand API exception handler for comprehensive error responses

**Type**: work

**Required Tools**: (none)

**Inputs**:
- All previous TODOs completed (new exception types may surface through API)

**Outputs**:
- `exception_handler_path` (file): `backend/src/main/kotlin/com/example/ledger/adapter/web/ApiExceptionHandler.kt`

**Steps**:
- [ ] Read current `ApiExceptionHandler.kt` — identify existing 3 handlers
- [ ] Add handler for `EthereumRpcException` → 502 Bad Gateway with JSON `{error: "Blockchain RPC error", detail: message}`
- [ ] Add handler for `WebClientResponseException` → 502/503 with status passthrough
- [ ] Add handler for `DataIntegrityViolationException` → 409 Conflict with generic message (no DB details exposed)
- [ ] Add handler for `NoSuchElementException` → 404 Not Found
- [ ] Add fallback handler for `Exception` → 500 Internal Server Error with generic message (NO stack trace)
- [ ] Write integration test `ApiExceptionHandlerIntegrationTest`: verify each new handler returns correct status code and safe JSON body

**Must NOT do**:
- Do not expose stack traces or internal error details in any response
- Do not change existing handler behavior (IllegalArgumentException → 400, etc.)
- Do not run git commands

**References**:
- `adapter/web/ApiExceptionHandler.kt:15-37` — existing 3 handlers
- `adapter/ethereum/dto/RpcResponse.kt` — EthereumRpcException definition

**Acceptance Criteria**:

*Functional:*
- [ ] `EthereumRpcException` → HTTP 502 + JSON body
- [ ] `DataIntegrityViolationException` → HTTP 409 + generic message
- [ ] `NoSuchElementException` → HTTP 404
- [ ] Unhandled `RuntimeException` → HTTP 500 + no stack trace
- [ ] Existing handlers unchanged (IllegalArgumentException → 400)

*Static:*
- [ ] `cd backend && ./gradlew compileKotlin` → exit 0

*Runtime:*
- [ ] `cd backend && ./gradlew test --tests "*ApiExceptionHandler*"` → passes
- [ ] `cd backend && ./gradlew test --tests "*JournalApiIntegrationTest*"` → passes (regression)

**Verify**:
```yaml
acceptance:
  - given: ["Controller throws EthereumRpcException"]
    when: "Request reaches ApiExceptionHandler"
    then: ["HTTP 502 returned", "JSON body has error field", "No stack trace in response"]
commands:
  - run: "cd backend && ./gradlew test --tests '*ApiExceptionHandler*'"
    expect: "exit 0"
risk: LOW
```

---

### [x] TODO 7: Add failure scenario integration tests

**Type**: work

**Required Tools**: (none)

**Inputs**:
- All previous TODOs completed

**Outputs**:
- `test_files` (list): `["FailureScenarioIntegrationTest.kt"]`

**Steps**:
- [ ] Create `FailureScenarioIntegrationTest.kt` extending `IntegrationTestBase`
- [ ] Test: RPC error during sync → wallet status transitions to FAILED
  - Mock `BlockchainDataPort` to throw `EthereumRpcException`
  - Call sync, verify wallet.syncStatus == FAILED
- [ ] Test: RPC error → retry on re-register → wallet recovers to COMPLETED
  - First sync fails (mock throws), wallet → FAILED
  - Re-register same wallet, mock succeeds → wallet → COMPLETED
- [ ] Test: CoinGecko down during sync → sync completes with price-less journals
  - Mock `PricePort` to return empty prices
  - Verify journals created with appropriate price source
- [ ] Test: Concurrent sync rejection returns safely
  - Use CountDownLatch to coordinate threads
  - Verify one sync executes, other is skipped
- [ ] Test: Concurrent wallet registration produces exactly 1 wallet
  - Use CountDownLatch for concurrent registerWallet calls
  - Verify DB count == 1
- [ ] Test: FIFO serialization retry succeeds
  - Two threads consume overlapping lots
  - Verify final quantities are correct (no errors with @Retryable)
- [ ] Run all new tests to ensure green

**Must NOT do**:
- Do not modify production code in this TODO
- Do not duplicate tests from previous TODOs' unit tests — focus on end-to-end failure scenarios
- Do not run git commands

**References**:
- `integration/IntegrationTestBase.kt` — test base class
- `integration/PipelineIntegrationTest.kt` — existing pipeline test pattern
- `integration/FifoConcurrencyIntegrationTest.kt` — existing concurrency test pattern

**Acceptance Criteria**:

*Functional:*
- [ ] All 6 failure scenarios tested
- [ ] Each test verifies the correct end state (wallet status, journal creation, DB row count)

*Static:*
- [ ] `cd backend && ./gradlew compileKotlin` → exit 0

*Runtime:*
- [ ] `cd backend && ./gradlew test --tests "*FailureScenarioIntegrationTest*"` → passes

**Verify**:
```yaml
acceptance:
  - given: ["Full test infrastructure (Testcontainers PostgreSQL)"]
    when: "FailureScenarioIntegrationTest runs"
    then: ["All 6 scenarios pass", "No flaky failures"]
commands:
  - run: "cd backend && ./gradlew test --tests '*FailureScenario*'"
    expect: "exit 0"
risk: LOW
```

---

### [x] TODO Final: Verification

**Type**: verification

**Required Tools**: `./gradlew`, `task`

**Inputs**:
- `rpc_client_path` (file): `${todo-1.outputs.rpc_client_path}`
- `sync_guard_path` (file): `${todo-2.outputs.sync_guard_path}`
- `wallet_ingest_path` (file): `${todo-3.outputs.wallet_ingest_path}`
- `retry_config_path` (file): `${todo-4.outputs.retry_config_path}`
- `ledger_service_path` (file): `${todo-4.outputs.ledger_service_path}`
- `coingecko_adapter_path` (file): `${todo-5.outputs.coingecko_adapter_path}`
- `exception_handler_path` (file): `${todo-6.outputs.exception_handler_path}`
- `test_files` (list): `${todo-7.outputs.test_files}`

**Outputs**: (none)

**Steps**:
- [ ] Run full backend test suite: `cd backend && ./gradlew test`
- [ ] Run full verify: `task verify` (backend:test + frontend:build)
- [ ] Verify all deliverables from Work Objectives exist
- [ ] Verify @EnableRetry is configured
- [ ] Verify custom JpaTransactionManager is registered
- [ ] Verify no stack traces in error responses (spot check ApiExceptionHandler tests)
- [ ] Verify FifoConcurrencyIntegrationTest expects errors.isEmpty() (not errors.size <= 1)

**Must NOT do**:
- Do not use Edit or Write tools (source code modification forbidden)
- Do not add new features or fix errors (report only)
- Do not run git commands
- Bash is allowed for: running tests, builds, type checks
- Do not modify repo files via Bash (no `sed -i`, `echo >`, etc.)

**Acceptance Criteria**:

*Functional:*
- [ ] All deliverables exist: EthereumRpcClient modified, SyncPipelineUseCase has trySetSyncing guard, IngestWalletUseCase has race condition fix, LedgerService has @Retryable, CoinGeckoAdapter has RetryExecutor, ApiExceptionHandler expanded
- [ ] @EnableRetry annotation present on LedgerApplication
- [ ] Custom JpaTransactionManager bean defined in TransactionConfig
- [ ] ensureAccountExists uses INSERT ON CONFLICT DO NOTHING

*Static:*
- [ ] `cd backend && ./gradlew compileKotlin` → exit 0

*Runtime:*
- [ ] `cd backend && ./gradlew test` → ALL tests pass (0 failures)
- [ ] `task verify` → exit 0 (backend test + frontend build)
