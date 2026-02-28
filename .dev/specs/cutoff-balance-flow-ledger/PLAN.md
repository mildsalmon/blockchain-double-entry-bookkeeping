# Cutoff Balance-Flow Ledger Plan

> Add cutoff snapshot + post-cutoff delta ingestion mode for quantity-based balance-flow tracking.
> Mode: standard/interactive

## Assumptions

> Decisions made without additional user roundtrip in this specify run.

| Decision Point | Assumed Choice | Rationale | Source |
|---------------|---------------|-----------|--------|
| Cutoff input contract | Canonical `cutoffBlock` (legacy `startBlock` accepted as alias) | Keep explicit semantics while preserving backward compatibility | compatibility-first |
| Snapshot source | `eth_getBalance` + ERC20 `balanceOf` at cutoff block | Most deterministic baseline for quantity mode | tradeoff recommendation |
| Checkpoint model | Separate `snapshotBlock` and `deltaSyncedBlock` | Prevent off-by-one/state-mixing bugs | tradeoff recommendation |
| Pipeline architecture | Dedicated balance-flow pipeline path with shared low-level adapters | Avoid coupling with pricing/FIFO path | tradeoff recommendation |
| Coverage scope v1 | ETH + ERC-20 transfers/logs only | Narrow initial scope, lower risk | risk-minimizing interpretation |
| Token discovery source | ETH + wallet `trackedTokens` set (persisted registry) | Deterministic completeness over implicit discovery | gap-analysis fix |
| Cutoff mutability | Immutable after first successful snapshot (reset required) | Prevent silent baseline drift | gap-analysis safeguard |

## Verification Summary

### Agent-Verifiable (A-items)
| ID | Criterion | Method | Related TODO |
|----|-----------|--------|-------------|
| A-1 | Existing wallet registration and base pipeline regressions stay green | `cd backend && ./gradlew test --tests '*WalletApiIntegrationTest' --tests '*PipelineIntegrationTest'` | TODO 6 |
| A-2 | New cutoff migration applies cleanly | `cd backend && ./gradlew test --tests '*FlywayMigrationsIntegrationTest'` | TODO 1 |
| A-3 | Snapshot-at-cutoff and delta-from-`cutoff+1` behavior is correct | `cd backend && ./gradlew test --tests '*CutoffPipelineIntegrationTest' --tests '*CutoffSnapshotIntegrationTest'` | TODO 6 |
| A-4 | Re-sync remains idempotent in cutoff mode | `cd backend && ./gradlew test --tests '*CutoffPipelineIntegrationTest'` | TODO 6 |
| A-5 | Balance-flow path bypasses KRW/FIFO/gain-loss logic | `cd backend && ./gradlew test --tests '*CutoffMode*Test'` | TODO 3, TODO 6 |
| A-6 | Frontend contract/build/lint remains green after cutoff UI changes | `cd frontend && npm run build && npm run lint` | TODO 5 |
| A-7 | End-to-end repository verification passes | `task verify` | TODO Final |

### Human-Required (H-items)
| ID | Criterion | Reason | Review Material |
|----|-----------|--------|----------------|
| H-1 | Cutoff option wording and mental model clarity | UX language judgment | Wallet form copy + status labels |
| H-2 | Snapshot/delta status readability for operators | Subjective operational usability | Wallet status UI |
| H-3 | Real-chain boundary spot-check (`cutoff`, `cutoff+1`, `cutoff+2`) | External ground truth needed | Explorer + runtime logs |
| H-4 | Runtime behavior under real RPC latency/rate limits | Environment dependent | staging/manual run report |

### Sandbox Agent Testing (S-items)
(none)
- Sandbox Tier-4 infra not present (`sandbox/`, `.feature` flows unavailable).

### Verification Gaps
- No browser E2E test harness for frontend behavior assertions.
- New cutoff-specific tests must be authored as part of this work (`*Cutoff*Test`).
- Real RPC archival behavior varies by provider; mocked tests cannot fully guarantee provider parity.

## External Dependencies Strategy

### Pre-work (user prepares before AI work)
| Dependency | Action | Command/Step | Blocking? |
|------------|--------|-------------|-----------|
| Local toolchain | Install project dependencies | `task setup` | Yes |
| JDK 17 | Ensure valid JAVA_HOME for backend builds | `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` | Yes |
| PostgreSQL | Start local DB | `task up` | Yes |
| Ethereum RPC endpoint | Provide archival-capable URL for manual validation | set `ETHEREUM_RPC_URL` in `.env` | Yes (for manual validation) |

### During (AI work strategy)
| Dependency | Dev Strategy | Rationale |
|------------|-------------|-----------|
| PostgreSQL | Flyway + Testcontainers integration tests | Deterministic schema/behavior checks |
| Ethereum JSON-RPC | Mock `BlockchainDataPort` in automated tests | Stable cutoff boundary verification |
| CoinGecko | Keep existing adapter untouched for cutoff mode | Explicitly out-of-scope for this phase |

### Post-work (user actions after completion)
| Task | Action | Command/Step |
|------|--------|-------------|
| Manual boundary verification | Compare logs around cutoff boundary | run app + check explorer at cutoff blocks |
| Operational readiness check | Validate sync latency and failure handling on real provider | `task dev` with real RPC |

## Context

### Original Request
지갑 등록 시 cutoff 시점 잔고를 먼저 저장하고, 이후 블록만 스캔해 잔고 흐름 원장을 구성한다. 가격 기반 손익 처리는 이번 범위에서 제외한다.

### Interview Summary
**Key Discussions**:
- Scope fixed to on-chain quantity flow tracking only.
- KRW valuation/FIFO/realized gain-loss deferred to future phase.
- Cutoff snapshot baseline + post-cutoff delta ingestion selected as target operating model.

**Research Findings**:
- Existing `startBlock` path already exists for wallet registration and sync start.
- Current pipeline couples ingestion -> classification -> pricing -> ledger generation.
- Wallet UI already accepts optional `startBlock` and polls sync status; additive UX extension is feasible.

### Assumptions
- Cutoff v1 uses canonical `cutoffBlock` semantics (`startBlock` alias only for compatibility) and immutable baseline after first successful snapshot.
- Event coverage for v1 is ETH/ERC-20 transfer scope only.
- Snapshot token universe in v1 is ETH + explicit wallet `trackedTokens` set.

### Compatibility Matrix (mode/cutoff/startBlock)
| mode | cutoffBlock | startBlock | Behavior |
|------|-------------|------------|----------|
| omitted | absent | absent/present | Legacy flow (existing behavior), `startBlock` handled as today |
| `BALANCE_FLOW_CUTOFF` | present | absent | Use `cutoffBlock` |
| `BALANCE_FLOW_CUTOFF` | absent | present | Use `startBlock` as deprecated alias for `cutoffBlock` |
| `BALANCE_FLOW_CUTOFF` | present | present | `cutoffBlock` wins, `startBlock` ignored (warning/trace log) |
| `BALANCE_FLOW_CUTOFF` | absent | absent | `400 Bad Request` |

## Work Objectives

### Core Objective
Enable fast wallet onboarding via cutoff baseline snapshot and post-cutoff delta ingestion while preserving existing full-mode behavior and excluding valuation logic.

### Concrete Deliverables
- New cutoff snapshot persistence model and migration.
- Balance-flow sync path with deterministic cutoff boundary (`cutoff + 1`).
- API/DTO updates exposing cutoff mode and snapshot/delta sync state.
- Frontend wallet UX updates for cutoff options and sync phase visibility.
- Cutoff-focused integration/unit regression suite.

### Definition of Done
- [ ] Cutoff wallet registration stores cutoff metadata; snapshot records are created during first sync run.
- [ ] Delta ingestion starts from `cutoff + 1` and remains idempotent across re-sync.
- [ ] Balance-flow mode does not invoke KRW pricing/FIFO/gain-loss computations.
- [ ] Existing non-cutoff behavior remains regression-safe.
- [ ] `task verify` passes.

### Must NOT Do (Guardrails)
- Do not backfill pre-cutoff ledger flows in this mode.
- Do not write KRW valuation, FIFO lots, or gain/loss artifacts in cutoff mode.
- Do not reuse single checkpoint semantics that mix snapshot and delta progress.
- Do not silently fallback to full-history scan on snapshot failure.
- Do not allow in-place cutoff mutation after successful snapshot creation.

## Task Flow

```
TODO-1 (schema/domain)
    ↓
TODO-2 (snapshot collection)
    ↓
TODO-3 (delta pipeline mode)
    ↓
TODO-4 (backend API/status contract)
    ↓
TODO-5 (frontend UX)
    ↓
TODO-6 (tests)
    ↓
TODO-Final (verification)
```

## Dependency Graph

| TODO | Requires (Inputs) | Produces (Outputs) | Type |
|------|-------------------|-------------------|------|
| 1 | - | `snapshot_schema_path` (file), `mode_fields_contract` (doc/string), `blockchain_port_contract` (file/string) | work |
| 2 | `${todo-1.outputs.snapshot_schema_path}`, `${todo-1.outputs.blockchain_port_contract}` | `snapshot_service_path` (file), `snapshot_repo_paths` (list[file]) | work |
| 3 | `${todo-1.outputs.mode_fields_contract}`, `${todo-2.outputs.snapshot_service_path}` | `cutoff_pipeline_path` (file) | work |
| 4 | `${todo-3.outputs.cutoff_pipeline_path}` | `wallet_api_contract_paths` (list[file]) | work |
| 5 | `${todo-4.outputs.wallet_api_contract_paths}` | `wallet_ui_paths` (list[file]) | work |
| 6 | `${todo-1.outputs.snapshot_schema_path}`, `${todo-3.outputs.cutoff_pipeline_path}`, `${todo-4.outputs.wallet_api_contract_paths}`, `${todo-5.outputs.wallet_ui_paths}` | `cutoff_test_paths` (list[file]) | work |
| Final | all outputs | - | verification |

## Parallelization

| Group | TODOs | Reason |
|-------|-------|--------|
| - | - | Sequential execution recommended due contract coupling and boundary-risk concentration |

## Commit Strategy

| After TODO | Message | Files | Condition |
|------------|---------|-------|-----------|
| 1 | `feat(sync): add cutoff snapshot schema and mode metadata` | migration + domain/port contracts | always |
| 2 | `feat(sync): implement cutoff balance snapshot collection` | snapshot service/repository adapters | always |
| 3 | `feat(sync): add cutoff delta pipeline mode with boundary isolation` | sync use case + mode routing | always |
| 4 | `feat(api): expose cutoff mode and sync phase contracts` | DTO/controller/usecase response updates | always |
| 5 | `feat(frontend): add cutoff onboarding and sync phase UI` | wallet input/status/types/api client | always |
| 6 | `test(sync): add cutoff boundary and idempotency regression suite` | backend + frontend test files | always |

## Error Handling

### Failure Categories

| Category | Examples | Detection Pattern |
|----------|----------|-------------------|
| `env_error` | DB/RPC unavailable | `/ECONNREFUSED|timeout|cannot connect/i` |
| `code_error` | compile/test failures | `/error:|FAILED|BUILD FAILED/i` |
| `scope_internal` | missing migration/contracts mismatch | missing required outputs or incompatible schema |
| `unknown` | unclassified failures | default fallback |

### Failure Handling Flow

| Scenario | Action |
|----------|--------|
| work fails | Retry up to 2 times, then analyze category |
| verification fails | Analyze immediately and report blocking items |
| missing input | Halt dependent TODOs and report |

### After Analyze

| Category | Action |
|----------|--------|
| `env_error` | halt + request environment readiness |
| `code_error` | create focused fix task (single depth) |
| `scope_internal` | adapt TODO with explicit amendment |
| `unknown` | halt and escalate with logs |

### Fix Task Rules
- Max dynamic fix depth: 1
- If fix task fails, halt pipeline
- Keep fix scope to failing TODO boundary

## Runtime Contract

| Aspect | Specification |
|--------|---------------|
| Working Directory | repository root |
| Network Access | allowed |
| Package Install | allowed only in pre-work (`task setup`); denied during worker TODO execution |
| File Access | repository only |
| Max Execution Time | 5 minutes per TODO target command |
| Git Operations | denied during worker TODO execution |

## TODOs

### [x] TODO 1: Add Cutoff Snapshot Data Model and Schema

**Type**: work

**Required Tools**: (none)

**Inputs**: (none)

**Outputs**:
- `snapshot_schema_path` (file): `backend/src/main/resources/db/migration/V*_cutoff_snapshot*.sql`
- `mode_fields_contract` (doc/string): wallet mode/checkpoint field contract
- `blockchain_port_contract` (file/string): `BlockchainDataPort` cutoff-balance methods contract

**Steps**:
- [ ] Add Flyway migration for wallet cutoff snapshot table(s) with uniqueness/indexes for wallet+token.
- [ ] Add schema for wallet `tracked_tokens` registry used by snapshot token scope.
- [ ] Extend wallet model/entity with explicit cutoff mode/checkpoint fields (`snapshotBlock`, `deltaSyncedBlock`, sync phase).
- [ ] Extend `BlockchainDataPort` contract for native/token balance-at-block reads.
- [ ] Add domain model + port interfaces for snapshot records.
- [ ] Keep schema additive and backward-compatible with existing wallet data.

**Must NOT do**:
- Do not modify existing valuation/FIFO tables for this phase.
- Do not remove or repurpose `lastSyncedBlock` semantics without compatibility handling.

**References**:
- `backend/src/main/resources/db/migration/V1__core_tables.sql`
- `backend/src/main/kotlin/com/example/ledger/domain/model/Wallet.kt`
- `backend/src/main/kotlin/com/example/ledger/adapter/persistence/entity/WalletEntity.kt`

**Acceptance Criteria**:

*Functional:*
- [ ] Snapshot schema exists and migrates successfully.
- [ ] Wallet mode/checkpoint fields support separate snapshot and delta phases.
- [ ] Port contract for cutoff balance reads is defined and wired through domain boundary.

*Static:*
- [ ] `cd backend && ./gradlew compileKotlin` passes.

*Runtime:*
- [ ] `cd backend && ./gradlew test --tests '*FlywayMigrationsIntegrationTest'` passes.

---

### [x] TODO 2: Implement Snapshot Collection at Cutoff

**Type**: work

**Required Tools**: (none)

**Inputs**:
- `snapshot_schema_path` (file): `${todo-1.outputs.snapshot_schema_path}`
- `blockchain_port_contract` (file/string): `${todo-1.outputs.blockchain_port_contract}`

**Outputs**:
- `snapshot_service_path` (file): cutoff snapshot collection use case/service path
- `snapshot_repo_paths` (list[file]): persistence adapter files

**Steps**:
- [ ] Implement snapshot collector that resolves ETH balance and all wallet `trackedTokens` balances at cutoff block.
- [ ] Persist baseline balances with chain/cutoff anchors.
- [ ] Add retry/error propagation aligned with existing RPC resilience utilities.
- [ ] Mark wallet sync phase transition to snapshot-complete state on success.

**Must NOT do**:
- Do not trigger full-history replay if snapshot fails.
- Do not write price-related fields while collecting snapshot.

**References**:
- `backend/src/main/kotlin/com/example/ledger/adapter/ethereum/EthereumRpcAdapter.kt`
- `backend/src/main/kotlin/com/example/ledger/application/usecase/IngestWalletUseCase.kt`

**Acceptance Criteria**:

*Functional:*
- [ ] Snapshot rows are created for ETH + exact wallet `trackedTokens` set at cutoff.
- [ ] Failure in snapshot collection sets wallet to failed phase/status.

*Static:*
- [ ] `cd backend && ./gradlew compileKotlin` passes.

*Runtime:*
- [ ] `cd backend && ./gradlew test --tests '*CutoffSnapshotIntegrationTest'` passes.

---

### [x] TODO 3: Add Dedicated Cutoff Delta Pipeline Mode

**Type**: work

**Required Tools**: (none)

**Inputs**:
- `mode_fields_contract` (doc/string): `${todo-1.outputs.mode_fields_contract}`
- `snapshot_service_path` (file): `${todo-2.outputs.snapshot_service_path}`

**Outputs**:
- `cutoff_pipeline_path` (file): cutoff-mode sync orchestration use case path

**Steps**:
- [ ] Introduce dedicated cutoff balance-flow pipeline route (or clearly isolated branch) from wallet mode.
- [ ] Enforce delta scanning boundary from `cutoff + 1` and persist `deltaSyncedBlock`.
- [ ] Keep idempotency guarantees for repeated sync invocations.
- [ ] Ensure cutoff mode bypasses price enrichment, FIFO lot operations, and gain/loss logic.
- [ ] Keep `lastSyncedBlock` compatibility mapping read-only for legacy status responses.

**Must NOT do**:
- Do not share mutable state that can cross-contaminate full mode and cutoff mode.
- Do not change tx dedupe key behavior (`tx_hash` uniqueness path) in this task.

**References**:
- `backend/src/main/kotlin/com/example/ledger/application/usecase/SyncPipelineUseCase.kt`
- `backend/src/main/kotlin/com/example/ledger/domain/service/LedgerService.kt`

**Acceptance Criteria**:

*Functional:*
- [ ] Delta ingestion starts strictly after cutoff block.
- [ ] Re-sync is idempotent and does not duplicate flow records.
- [ ] Cutoff mode does not call valuation/FIFO pathways.

*Static:*
- [ ] `cd backend && ./gradlew compileKotlin` passes.

*Runtime:*
- [ ] `cd backend && ./gradlew test --tests '*CutoffPipelineIntegrationTest' --tests '*CutoffMode*Test'` passes.

---

### [x] TODO 4: Extend Backend API Contracts for Cutoff Mode and Sync Phase

**Type**: work

**Required Tools**: (none)

**Inputs**:
- `cutoff_pipeline_path` (file): `${todo-3.outputs.cutoff_pipeline_path}`

**Outputs**:
- `wallet_api_contract_paths` (list[file]): updated DTO/controller/use case contract files

**Steps**:
- [ ] Extend wallet registration DTO for explicit mode and cutoff fields.
- [ ] Define canonical request shape (`mode`, `cutoffBlock`, `trackedTokens`) and legacy alias (`startBlock`) precedence via explicit matrix.
- [ ] Define `trackedTokens` lifecycle: provided at registration for cutoff mode, normalized (lowercase checksum/unique), persisted immutable with cutoff baseline.
- [ ] Extend wallet status/list responses with snapshot/delta phase metadata.
- [ ] Keep backward compatibility for existing clients using legacy registration payload.
- [ ] Document API contract semantics in spec comments or API docs.
- [ ] Enforce cutoff immutability after first successful snapshot (return `409` on mutation attempt).

**Must NOT do**:
- Do not introduce breaking required fields for existing clients.
- Do not expose internal exception details in API responses.

**References**:
- `backend/src/main/kotlin/com/example/ledger/application/dto/WalletDtos.kt`
- `backend/src/main/kotlin/com/example/ledger/adapter/web/WalletController.kt`
- `backend/src/main/kotlin/com/example/ledger/application/usecase/IngestWalletUseCase.kt`

**Acceptance Criteria**:

*Functional:*
- [ ] Wallet create/status API supports cutoff mode semantics.
- [ ] Canonical/legacy request precedence matrix is deterministic, documented, and covered by API tests.
- [ ] `trackedTokens` registration validation/normalization/immutability is enforced and covered by API tests.
- [ ] Cutoff mutation after baseline is rejected with `409` and no DB change.
- [ ] Existing wallet API tests remain green.

*Static:*
- [ ] `cd backend && ./gradlew compileKotlin` passes.

*Runtime:*
- [ ] `cd backend && ./gradlew test --tests '*WalletApiIntegrationTest' --tests '*CutoffWalletApiIntegrationTest'` passes (including precedence + trackedTokens + immutability cases).

---

### [x] TODO 5: Update Frontend Wallet UX for Cutoff and Sync Phases

**Type**: work

**Required Tools**: (none)

**Inputs**:
- `wallet_api_contract_paths` (list[file]): `${todo-4.outputs.wallet_api_contract_paths}`

**Outputs**:
- `wallet_ui_paths` (list[file]): updated wallet form/status/type/api client files

**Steps**:
- [ ] Update wallet input to expose cutoff mode options near current `startBlock` input.
- [ ] Add tracked token input UX for cutoff mode (address list) with client-side format validation.
- [ ] Extend frontend wallet types and API client payload/response mappings.
- [ ] Render snapshot/delta phase in sync status cards.
- [ ] Preserve existing wallet onboarding and polling behavior.

**Must NOT do**:
- Do not add valuation-related UX fields in this phase.
- Do not introduce new required inputs that block existing quick wallet registration.

**References**:
- `frontend/src/components/WalletInput.tsx`
- `frontend/src/components/SyncStatus.tsx`
- `frontend/src/lib/api.ts`
- `frontend/src/types/wallet.ts`

**Acceptance Criteria**:

*Functional:*
- [ ] User can submit cutoff mode registration payload from UI.
- [ ] User can provide tracked token list for cutoff mode and see validation feedback.
- [ ] Sync status displays cutoff pipeline phases.

*Static:*
- [ ] `cd frontend && npm run build` passes.
- [ ] `cd frontend && npm run lint` passes.

*Runtime:*
- [ ] (No additional automated runtime command; validated under H-1/H-2 manual review.)

---

### [x] TODO 6: Add Cutoff Boundary and Regression Test Suite

**Type**: work

**Required Tools**: (none)

**Inputs**:
- `snapshot_schema_path` (file): `${todo-1.outputs.snapshot_schema_path}`
- `cutoff_pipeline_path` (file): `${todo-3.outputs.cutoff_pipeline_path}`
- `wallet_api_contract_paths` (list[file]): `${todo-4.outputs.wallet_api_contract_paths}`
- `wallet_ui_paths` (list[file]): `${todo-5.outputs.wallet_ui_paths}`

**Outputs**:
- `cutoff_test_paths` (list[file]): new/updated cutoff-mode unit and integration tests

**Steps**:
- [ ] Add integration tests for cutoff boundary (`cutoff` excluded, `cutoff+1` included).
- [ ] Add idempotent re-sync test for cutoff mode.
- [ ] Add token-scope completeness test: snapshot rows exactly match ETH + wallet `trackedTokens`.
- [ ] Add immutability test: cutoff mutation attempt returns `409` and DB remains unchanged.
- [ ] Add regression tests proving valuation/FIFO paths are bypassed in cutoff mode.
- [ ] Re-run existing pipeline/wallet regression suites.

**Must NOT do**:
- Do not weaken existing assertions in current pipeline tests.
- Do not skip failure-path tests for snapshot errors.

**References**:
- `backend/src/test/kotlin/com/example/ledger/integration/PipelineIntegrationTest.kt`
- `backend/src/test/kotlin/com/example/ledger/integration/WalletApiIntegrationTest.kt`
- `backend/src/test/kotlin/com/example/ledger/integration/IntegrationTestBase.kt`

**Acceptance Criteria**:

*Functional:*
- [ ] Cutoff mode correctness tests pass.
- [ ] Existing regression tests remain green.
- [ ] Snapshot token universe correctness is asserted by integration test.
- [ ] Cutoff immutability is asserted by API/domain integration test.

*Static:*
- [ ] `cd backend && ./gradlew compileTestKotlin` passes.

*Runtime:*
- [ ] `cd backend && ./gradlew test --tests '*Cutoff*' --tests '*PipelineIntegrationTest' --tests '*WalletApiIntegrationTest'` passes.

---

### [x] TODO Final: Verification

**Type**: verification

**Required Tools**: `./gradlew`, `npm`, `task`

**Inputs**:
- `snapshot_schema_path` (file): `${todo-1.outputs.snapshot_schema_path}`
- `snapshot_service_path` (file): `${todo-2.outputs.snapshot_service_path}`
- `cutoff_pipeline_path` (file): `${todo-3.outputs.cutoff_pipeline_path}`
- `wallet_api_contract_paths` (list[file]): `${todo-4.outputs.wallet_api_contract_paths}`
- `wallet_ui_paths` (list[file]): `${todo-5.outputs.wallet_ui_paths}`
- `cutoff_test_paths` (list[file]): `${todo-6.outputs.cutoff_test_paths}`

**Outputs**: (none)

**Steps**:
- [ ] Verify migration files and schema wiring exist.
- [ ] Run backend focused tests for cutoff and regressions.
- [ ] Run frontend build/lint checks.
- [ ] Run full verification gate.
- [ ] Confirm no pricing/FIFO writes occur in cutoff mode path via test assertions/log checks.

**Must NOT do**:
- Do not modify source files in this TODO.
- Do not add new feature scope while verifying.

**Acceptance Criteria**:

*Functional:*
- [ ] Cutoff snapshot + delta behavior validated by tests.
- [ ] Non-cutoff flow remains fully operational.

*Static:*
- [ ] `cd backend && ./gradlew compileKotlin compileTestKotlin` passes.
- [ ] `cd frontend && npm run build && npm run lint` passes.

*Runtime:*
- [ ] `cd backend && ./gradlew test` passes.
- [ ] `task verify` passes.
