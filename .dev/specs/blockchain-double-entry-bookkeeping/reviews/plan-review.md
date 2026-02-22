---
title: Plan Review — Blockchain Double-Entry Bookkeeping
date: 2026-02-22
round: 2
consensus: claude+codex (Gemini degraded)
verdict: SHIP
---

# Plan Review Results — Round 2

**Consensus**: Claude + Codex (Gemini degraded at review time)
**Verdict**: **SHIP** — All 14 previous findings resolved. New findings are Minor or have straightforward fixes.

---

## Round 1 CR Verification (14/14 FIXED)

| CR ID | Status | Verified By | Notes |
|-------|--------|-------------|-------|
| CR-001 | FIXED | Claude, Codex | Task 9: `BigDecimal(MathContext.DECIMAL128)` + HALF_UP rounding + precision test |
| CR-002 | FIXED | Claude, Codex | Task 4: `TransactionConfig.kt` with SERIALIZABLE. Task 9 applies it. Task 17 race condition test |
| CR-003 | FIXED | Claude, Codex | Task 2d: `account_code FK → accounts(code)`. Task 12a: system account code/delete protection |
| CR-004 | FIXED | Claude, Codex | Task 3: `validateEditable()` invariant. Task 12b: domain-level 400 rejection. Task 17: bulk-approve test |
| CR-005 | FIXED | Claude, Codex | Task 3: `DecodedTransaction` domain model. Task 7: Raw→Decoded. Task 8: classifies Decoded. Task 10: pipeline includes decode step |
| CR-006 | FIXED | Claude, Codex | Tasks 9a/9b/9c merged into atomic Task 9 with FIFO + gain/loss in single pass |
| CR-007 | FIXED | Claude, Codex | Task 6: `/market_chart/range` batch API. O(tokens) calls. Task 17: NFR-1 perf test |
| CR-008 | FIXED | Claude, Codex | Task 1: CORS via `WebMvcConfigurer` or Next.js proxy rewrite |
| CR-009 | FIXED | Claude, Codex | Task 11: `AuditServiceImpl` in `adapter/persistence/`. Interface in `domain/service/` |
| CR-010 | FIXED | Claude, Codex | Task 2d: `raw_transaction_id FK`. Task 9: sets FK. Task 14: frontend grouping |
| CR-011 | FIXED | Claude, Codex | Task 12c: optional `wallet_address` filter on export |
| CR-012 | FIXED | Claude, Codex | Task 12b: manual classify → update event + classifier_id="MANUAL" + ledger engine + MANUAL_CLASSIFIED |
| CR-013 | FIXED | Claude, Codex | Task 17: frontend E2E manual for MVP, Playwright post-MVP |
| CR-014 | FIXED | Claude, Codex | Task 2a: `last_synced_block BIGINT`. Task 10: incremental sync |

---

## New Findings

### CR-015: ERC-20 dynamic account provisioning not specified [Consensus: Codex raised, Claude did not flag]
- **Severity**: Major
- **Category**: COMPLETENESS
- **Impact**: `journal_lines.account_code` has FK to `accounts(code)`. Seed data (Task 2e) only provides template `자산:암호화폐:ERC20:*`. When the ledger engine generates a journal entry for a new ERC-20 token (e.g., USDC), no `자산:암호화폐:ERC20:USDC` account exists in the `accounts` table. The FK constraint will fail at runtime.
- **Recommendation**: Add auto-provisioning logic to Task 9 (LedgerService): when generating a journal entry for a previously unseen ERC-20 token, auto-create `자산:암호화폐:ERC20:{SYMBOL}` account with `is_system=true`, `category=ASSET`. Add acceptance criterion to Task 9.

### CR-016: price_cache unique key uses token_symbol — may collide for tokens with same symbol [Codex only]
- **Severity**: Minor
- **Category**: CONSISTENCY
- **Impact**: Two tokens could share the same symbol (e.g., fake USDT). In MVP (Ethereum mainnet only, well-known tokens), collision risk is very low. For robustness, keying by `token_address` + `price_date` would be safer.
- **Recommendation**: Consider changing `price_cache` unique constraint to `(token_address, price_date, source)` instead of `(token_symbol, price_date, source)`. Can be deferred to post-MVP if needed.

### CR-017: Same-block transaction ordering not deterministic in schema [Codex only]
- **Severity**: Minor
- **Category**: CONSISTENCY
- **Impact**: Within the same block, multiple transactions may exist. FIFO lot consumption order depends on tx ordering. The `raw_transactions` table has `block_number` but no `tx_index` or `log_index`. Alchemy `getAssetTransfers` returns ordered results, and the ordering data IS available in `raw_data` JSON, but isn't explicitly captured in schema columns.
- **Recommendation**: Add `tx_index INTEGER` to `raw_transactions` table for deterministic ordering. Can be extracted from raw_data during ingestion. Not blocking for MVP since Alchemy preserves ordering.

### CR-018: NFR-2 (2-second page load) has no task coverage [Consensus: both Claude and Codex flagged]
- **Severity**: Minor
- **Category**: COMPLETENESS
- **Impact**: No database indexes specified for `journal_entries(entry_date, status)` or `journal_lines(account_code)` filter queries. No acceptance criterion validates the 2-second threshold.
- **Recommendation**: Add indexes to Task 2d migration. Add timing assertion to Task 12b or Task 17.

### CR-019: Dependency graph minor inconsistencies [Claude only]
- **Severity**: Minor
- **Category**: DEPENDENCY
- **Impact**: (a) ASCII graph shows Task 8 depends on "2c, 7" but table also lists Task 5. (b) Task 11 is shown after Task 10 but could start earlier (after Task 3 + 2a), reducing critical path.
- **Recommendation**: Align ASCII graph and parallel execution table. Move Task 11 to earlier phase.

### CR-020: ERC-721/ERC-1155 collection vs classification gap not documented [Claude only]
- **Severity**: Minor
- **Category**: COMPLETENESS
- **Impact**: FR-2 requires collecting ERC-721/1155 transfers. Task 5 will collect them via Alchemy, but no classifier exists (intentionally — NFT valuation is out of scope). An implementer might assume classifiers are needed.
- **Recommendation**: Add note to Task 8 MUST NOT DO: "ERC-721/ERC-1155 are collected at Layer 1 but classified as UNCLASSIFIED in MVP."

---

## Findings Summary

| Severity | Count | IDs |
|----------|-------|-----|
| Critical | 0 | — |
| Major | 1 | CR-015 |
| Minor | 5 | CR-016, CR-017, CR-018, CR-019, CR-020 |

---

## Model Agreement

| Finding | Claude | Codex | Consensus |
|---------|--------|-------|-----------|
| CR-015 (ERC-20 account provisioning) | — | Major | Major (valid gap) |
| CR-016 (price_cache key) | — | Major→Minor | Minor (low risk in MVP) |
| CR-017 (tx ordering) | — | Major→Minor | Minor (data available in JSON) |
| CR-018 (NFR-2 coverage) | Minor | Minor | Minor (consensus) |
| CR-019 (dependency graph) | Minor | — | Minor |
| CR-020 (ERC-721/1155 note) | Minor | — | Minor |

**Codex flagged 2 additional items downgraded during synthesis:**
- "Manual classify user-entered accounts" → Already covered by existing PATCH flow (modify after auto-generation)
- "NFR-1 vs Risk section inconsistency" → Documentation tone issue, not a technical gap; risk section acknowledges cold-start reality

---

## Spec Coverage (Updated)

All 29 FRs covered. NFR status:
- **NFR-1**: Covered (batch price API + performance test in Task 17)
- **NFR-2**: Partially covered (no explicit index/test — see CR-018)
- **NFR-3**: Covered (BigDecimal DECIMAL128 in Task 9)
- **NFR-4**: Covered (SERIALIZABLE in Task 4, race condition test in Task 17)
- **NFR-5**: Covered (Docker Compose in Task 1)

---

## Verdict Rationale

**SHIP** — The 1 Major finding (CR-015: ERC-20 account auto-provisioning) has a straightforward fix that can be addressed at the start of implementation (adding 2-3 lines to Task 9's MUST DO section). It does not require architectural changes or task restructuring. All 5 Minor findings are documentation improvements or performance optimizations addressable during implementation.

The plan is architecturally sound, all 14 previous findings have been verified as properly fixed, and the dependency graph is correct for implementation to proceed.
