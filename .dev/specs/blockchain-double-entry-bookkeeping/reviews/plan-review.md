---
title: Plan Review — Blockchain Double-Entry Bookkeeping
date: 2026-02-23
round: 4
consensus: 3-model (Claude + Codex + Gemini)
verdict: SHIP
---

# Plan Review Results — Round 4

**Consensus**: 3-model (Claude + Codex + Gemini)
**Verdict**: **SHIP** — Plan is comprehensive, covers all 29 FR and 5 NFR requirements. Findings are implementation-time refinements, not architectural blockers.

---

## Round 1-3 CR Status

All previous CRs (CR-001 through CR-031) remain addressed. No regressions.

---

## New Findings (Round 4)

### CR-032: `raw_transactions.tx_hash UNIQUE` conflicts with multi-wallet tracking
- **Severity**: MEDIUM (Codex: CRITICAL → downgraded)
- **Category**: correctness
- **Models**: Codex
- **Finding**: If two tracked wallets are both involved in the same transaction, `tx_hash UNIQUE` forces deduplication — one wallet's context may be lost.
- **Recommendation**: In MVP, single-wallet usage is the primary scenario. For multi-wallet, add implementation note to Task 2b: when saving a duplicate tx_hash, verify wallet_address matches or create a junction table. Not blocking for MVP since the plan's Out of Scope section implies single-entity focus.
- **Consensus**: Downgraded from CRITICAL. The plan's data model stores `wallet_address` per raw_transaction row. For same-tx-different-wallet, the implementation can use `ON CONFLICT DO NOTHING` and link via the existing `wallet_address` column. This is an implementation detail, not a plan architecture issue.

### CR-033: Journal amount edits can desync FIFO lots and realized P/L
- **Severity**: MEDIUM (Codex: CRITICAL → downgraded)
- **Category**: consistency
- **Models**: Codex
- **Finding**: Task 12b allows PATCH on journal amount/account after Task 9 has computed FIFO lots and gain/loss. Edits could desynchronize journal lines from `cost_basis_lots`.
- **Recommendation**: Add a clarification to Task 12b: journal amount edits should only apply to the KRW display amount and memo, NOT to token quantities that affect FIFO state. Token-quantity changes require pipeline re-run. This aligns with the plan's existing CR-004 (APPROVED entries are immutable) — extend to protect FIFO-linked fields.
- **Consensus**: Downgraded from CRITICAL. The plan already restricts APPROVED entry edits (CR-004). The gap is for non-approved entries, which is an implementation-time validation rule, not a plan architecture issue.

### CR-034: Native ETH block scanning needs start_block strategy / Internal transactions missed
- **Severity**: MEDIUM
- **Category**: feasibility
- **Models**: Codex (HIGH), Gemini (MEDIUM), Claude (LOW)
- **Finding**: Task 5's block scanning via `eth_getBlockByNumber` is expensive for full-history wallets. Additionally, smart contract-initiated ETH transfers ("internal transactions") are invisible to standard block scanning.
- **Recommendation**: (1) Add configurable `start_block` parameter to wallet registration for bounding initial scan. (2) Document internal transactions as a known limitation of standard JSON-RPC in the plan's Risks table. (3) The plan already mitigates via `last_synced_block` incremental sync and acknowledges initial sync may exceed NFR-1.
- **Consensus**: MEDIUM. The plan's risk table already documents this. Internal transactions are a fundamental JSON-RPC limitation, not a plan defect. Document as known limitation.

### CR-035: Uniswap Swap event wallet filtering is under-specified
- **Severity**: MEDIUM
- **Category**: correctness
- **Models**: Codex (HIGH), Claude (LOW)
- **Finding**: Swap events don't have the wallet address in indexed topics, so eth_getLogs cannot filter by wallet. The plan says "Swap 이벤트 토픽 필터링" but doesn't specify how wallet relevance is determined. Without filtering, all Swap events in the block range are collected.
- **Recommendation**: Add clarification to Task 5: Swap events should be collected via receipt logs of wallet-attributed transactions (from block scanning in step 1), not via broad eth_getLogs topic scans. This avoids massive over-collection.
- **Consensus**: MEDIUM. Important implementation clarification but the plan's intent is clear — the adapter should only process wallet-relevant transactions.

### CR-036: Multi-wallet internal transfers produce incorrect gain/loss
- **Severity**: LOW (Gemini: HIGH → downgraded)
- **Category**: completeness
- **Models**: Gemini
- **Finding**: If a user registers Wallet A and Wallet B, sending ETH from A to B would generate OUTGOING (with disposal gain/loss) at A and INCOMING (new acquisition) at B. This is incorrect — internal transfers should preserve cost basis.
- **Recommendation**: Add to post-MVP backlog. The plan's MVP scope is single-wallet focused. Multi-wallet entity management is listed in Out of Scope ("멀티유저/인증 시스템"). When implemented, add `INTERNAL_TRANSFER` event type.
- **Consensus**: Downgraded from HIGH. Explicitly out of MVP scope per plan.

### CR-037: Undefined placeholder accounts in Task 9 ledger mapping
- **Severity**: MEDIUM
- **Category**: correctness
- **Models**: Codex (HIGH)
- **Finding**: Task 9 uses placeholders like `(미지정수입)` and `(상대계정)` in journal mapping rules, but Task 2e seed data doesn't define these concrete accounts. Runtime FK failures could occur.
- **Recommendation**: Add 2 system accounts to Task 2e seed data: `수익:미지정수입` (REVENUE) for incoming transfers with unknown source, and `자산:외부` (ASSET) as generic counterparty. Update Task 9 mapping rules to reference these concrete account codes.
- **Consensus**: MEDIUM. Valid finding — the plan should specify concrete account codes for all mapping rules. Addressable by adding seed data entries.

### CR-038: Transaction ordering not specified in pipeline
- **Severity**: MEDIUM
- **Category**: correctness
- **Models**: Claude (LOW → upgraded)
- **Finding**: Task 10 pipeline processes transactions without explicit ordering. FIFO lot creation/consumption depends on chronological order — processing out of order produces incorrect cost basis.
- **Recommendation**: Add to Task 10 acceptance criteria: "파이프라인은 트랜잭션을 (blockNumber, txIndex) 순으로 정렬 후 처리해야 한다." The data model already supports this via `block_number` and `tx_index` fields.
- **Consensus**: Upgraded to MEDIUM. FIFO correctness depends on processing order. Simple fix but important to specify.

### CR-039: Blockchain reorganization handling
- **Severity**: LOW (Codex: MEDIUM, Gemini: HIGH → downgraded)
- **Category**: correctness
- **Models**: Codex, Gemini
- **Finding**: `last_synced_block` incremental sync is vulnerable to chain reorgs — orphaned block data could persist.
- **Recommendation**: Document as known limitation in Risks table. For MVP, process only finalized blocks (tip - 32). Post-MVP, add confirmation buffer re-scan.
- **Consensus**: Downgraded to LOW. The plan's Risks section already lists "체인 리오그(reorg) 처리" as a Minor risk with "finalized 블록만 처리" as mitigation. This is explicitly in the Out of Scope section.

### CR-040: Adaptive eth_getLogs chunking for provider response limits
- **Severity**: LOW
- **Category**: feasibility
- **Models**: Codex (MEDIUM)
- **Finding**: Fixed 10,000-block chunking may fail when providers enforce response-size limits even within that range.
- **Recommendation**: Add implementation note to Task 5: on "too many results" error, halve the block range and retry. The plan already specifies retry infrastructure in Task 4.
- **Consensus**: LOW. Implementation-time optimization, not a plan issue.

### CR-041: CSV export loses multi-line journal entries
- **Severity**: LOW
- **Category**: completeness
- **Models**: Claude
- **Finding**: Task 12c implies single-row-per-entry CSV format, but journal entries can have 3+ lines (debit asset + credit asset + gain/loss). Multi-line data would be lost.
- **Recommendation**: Clarify in Task 12c that CSV should output one row per journal line, not per entry.
- **Consensus**: LOW. Export format detail, not architectural.

### CR-042: Spec FR-2 missing eth_getTransactionByHash and eth_blockNumber
- **Severity**: LOW
- **Category**: consistency
- **Models**: Claude
- **Finding**: FR-2 lists only 3 RPC methods but the plan and implementation also use `eth_getTransactionByHash` and `eth_blockNumber`.
- **Recommendation**: Update spec FR-2 to include all 5 standard RPC methods used. Documentation alignment only.

### CR-043: SyncPipelineUseCase imports adapter-layer EventDecoder (hexagonal violation)
- **Severity**: LOW
- **Category**: architecture
- **Models**: Claude
- **Finding**: Application-layer use case directly imports adapter-layer class. Plan Task 3 doesn't define a DecoderPort interface.
- **Recommendation**: Add `TransactionDecoderPort` to Task 3 port definitions. Minor architecture clean-up.

---

## Findings Summary

| Severity | Count | IDs |
|----------|-------|-----|
| Critical | 0 | — |
| High | 0 | — |
| Medium | 5 | CR-032, CR-033, CR-035, CR-037, CR-038 |
| Low | 6 | CR-034, CR-036, CR-039, CR-040, CR-041, CR-042, CR-043 |

---

## Model Agreement Matrix

| Finding | Claude | Codex | Gemini | Consensus |
|---------|--------|-------|--------|-----------|
| CR-032 (tx_hash UNIQUE multi-wallet) | — | CRITICAL | — | MEDIUM (MVP single-wallet) |
| CR-033 (Journal edit FIFO desync) | — | CRITICAL | — | MEDIUM (CR-004 covers APPROVED) |
| CR-034 (Block scan + internal tx) | LOW | HIGH | MEDIUM | MEDIUM |
| CR-035 (Swap wallet filtering) | LOW | HIGH | — | MEDIUM |
| CR-036 (Multi-wallet internal transfer) | — | — | HIGH | LOW (out of MVP scope) |
| CR-037 (Undefined placeholder accounts) | — | HIGH | — | MEDIUM |
| CR-038 (Transaction ordering) | LOW | — | — | MEDIUM (FIFO critical) |
| CR-039 (Chain reorg) | — | MEDIUM | HIGH | LOW (documented risk) |
| CR-040 (Adaptive chunking) | — | MEDIUM | — | LOW |
| CR-041 (CSV multi-line) | LOW | — | — | LOW |
| CR-042 (Spec FR-2 methods) | LOW | — | — | LOW |
| CR-043 (Hexagonal violation) | LOW | — | — | LOW |

**Downgrade rationale**:
- Codex's 2 CRITICAL findings (CR-032, CR-033) are valid concerns but fall within MVP's single-wallet scope (CR-032) and existing CR-004 protection (CR-033). Neither requires architectural plan changes.
- Gemini's HIGH findings (CR-036 reorg, CR-036 internal transfers) are explicitly addressed in the plan's Risks and Out of Scope sections.
- All 5 MEDIUM findings are addressable via implementation-time notes and seed data additions — no task restructuring needed.

---

## Spec Coverage (Full)

| Requirement Group | Tasks | Status |
|-------------------|-------|--------|
| FR-1~FR-5 (Data Collection) | Task 5 | Covered |
| FR-6~FR-9 (Classification) | Task 8 | Covered |
| FR-10~FR-14 (Journal + FIFO + Accounts) | Task 9 | Covered |
| FR-15~FR-18 (Audit Trail) | Task 11 | Covered |
| FR-19~FR-23 (Review UI) | Tasks 12b, 14, 15 | Covered |
| FR-24~FR-26 (Export) | Tasks 12c, 16 | Covered |
| FR-27~FR-29 (Pricing) | Task 6 | Covered |
| NFR-1 (5min/1000tx) | Task 17 | Covered |
| NFR-2 (2sec page load) | Task 2d indexes | Covered |
| NFR-3 (18 decimal precision) | NUMERIC(38,18) | Covered |
| NFR-4 (SERIALIZABLE) | Task 4 | Covered |
| NFR-5 (Docker Compose) | Task 1 | Covered |

---

## Verdict Rationale

**SHIP** — The plan is comprehensive and well-structured across 22 tasks with correct dependency ordering. All 29 functional and 5 non-functional requirements are covered. The 3-model review produced 12 findings, but after synthesis:
- 0 Critical, 0 High findings
- 5 MEDIUM findings are implementation-time refinements (seed data, ordering, filtering clarifications)
- 6 LOW findings are documentation/minor clean-ups
- No architectural changes or task restructuring required

The plan is ready for `/implement`. MEDIUM findings should be addressed during implementation as inline notes.

---

## Post-Implement Hardening (2026-02-23)

Round 4 findings 중 즉시 반영 가능한 항목을 구현/테스트로 보강했다.

### Applied

- [x] CR-038 (Transaction ordering): `SyncPipelineUseCase`에서 raw tx 처리 순서 `(blockNumber, txIndex, txHash)` 정렬 고정
- [x] CR-033 (Journal edit FIFO desync): `JournalEntry.update()`에 token 메타데이터(`tokenSymbol`, `tokenQuantity`) 불변식 추가
- [x] CR-035 (Swap wallet filtering): `EthereumRpcAdapter`에서 지갑 토픽 포함 로그만 수집, 비연관 Swap 로그 과수집 방지
- [x] CR-043 (Hexagonal violation): `TransactionDecoderPort` 도입, `SyncPipelineUseCase`가 adapter 구현이 아닌 port에 의존

### Verification

- [x] `backend ./gradlew test` 전체 통과
- [x] `frontend npm run build` 통과
- [x] 보강 회귀 테스트 추가/통과:
  - `JournalEntryTest` (token metadata 변경 차단)
  - `JournalApiIntegrationTest` (token quantity 변경 PATCH 400)
  - `PipelineIntegrationTest` (역순 입력에서도 FIFO 손익 정합성)
  - `EthereumRpcAdapterLogFilterTest` (지갑 비연관 Swap 로그 제외)
