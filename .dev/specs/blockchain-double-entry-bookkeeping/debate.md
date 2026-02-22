---
title: 3-Model Debate — Blockchain Double-Entry Bookkeeping
date: 2026-02-22
consensus: 3-model
models: [claude, codex, gemini]
---

# Multi-Model Debate Summary

## Participants
- **Claude** (Critic): Risk analysis and edge case identification
- **Codex** (Architect): Implementation complexity and feasibility assessment
- **Gemini** (Architect): Cross-ecosystem learnings and blind spot analysis

---

## Points of Agreement (All 3 Models)

1. **Transaction interpretation is the core technical challenge** — not ingestion, not the ledger engine. The "semantic gap" between on-chain events and accounting intent is the defining difficulty.

2. **Three-layer architecture is essential**:
   - Layer 1: Raw immutable chain data
   - Layer 2: Normalized/classified accounting events
   - Layer 3: Double-entry journal entries
   - All models independently recommended this separation for reprocessing, auditability, and rule evolution.

3. **MVP must be ruthlessly scoped** — ETH transfers, ERC-20 transfers, gas fees, and 1-2 swap protocols (Uniswap V2/V3) only. "Unclassified" as a first-class concept for everything else.

4. **Manual override and audit trail are non-negotiable** — Accountants are personally liable. Every journal entry must link back to source tx hash, classification rule, and price source. Override history must be preserved.

5. **Plugin/adapter architecture for DeFi protocols** — DeFi support is infinite scope. Bounded per-protocol adapters prevent monolithic growth.

6. **Price/valuation engine is critical infrastructure** — Not an afterthought. Must stamp every event with fiat value (KRW for Korean market), with source provenance and confidence scores.

---

## Points of Divergence

### Ledger Engine: medici (MongoDB) vs Custom PostgreSQL

| Model | Position | Reasoning |
|-------|----------|-----------|
| Claude | Neutral (medici as starting point) | medici provides quick double-entry enforcement; swap later if needed |
| Codex | Start with library, abstract behind interface | Faster time-to-market; accept possible migration cost |
| Gemini | **Recommends PostgreSQL over MongoDB** | Financial data is relational; FIFO lot tracking, "holdings at block N" queries, and complex reporting are significantly harder in MongoDB |

**Resolution needed**: This is a key architectural decision for the /specify phase. Trade-off is speed-to-MVP (medici) vs long-term query capability (PostgreSQL).

### Starting Scope: Ethereum vs Simpler Chain

| Model | Position |
|-------|----------|
| Claude | Consider starting with a simpler EVM chain (Polygon/Arbitrum) — Ethereum has the most edge cases |
| Codex | Ethereum is fine if scope is narrowed to basic transfer archetypes |
| Gemini | Ethereum is correct starting point (richest data, most demand) |

**Resolution**: Consensus leans toward Ethereum with strict scope limits.

### Product Framing: Accounting System vs Audit Workbench

| Model | Position |
|-------|----------|
| Claude | Full accounting system with audit capability |
| Codex | Start as "audit workbench + export" before full accounting ownership |
| Gemini | "Review & Confirm" UI inspired by Xero/QuickBooks reconciliation flow |

**Resolution**: Codex and Gemini converge on a lighter initial framing — generate draft journal entries that accountants review/confirm, rather than a fully automated accounting system.

---

## Unique Insights by Model

### Claude (Critic) — Unique Contributions
- **Rebasing tokens** (stETH, AMPL) change balances without Transfer events — invisible to event-based scanning
- **Flash loans** create misleading journal entries ($50M debit that nets to ~$0)
- **Airdrops vs dust attacks** have identical on-chain signatures — cannot auto-classify
- **Stablecoin materiality threshold** — sub-cent fluctuations generate noise; need configurable tolerance
- **Multi-address entity** design needed from day one (DAOs use multiple wallets, multi-sigs, vesting contracts)

### Codex (Architect) — Unique Contributions
- **Event-sourced architecture** — raw chain events immutable, accounting interpretations versioned, enabling reclassification
- **Price-source provenance + confidence score** — not just "what price" but "where from, how reliable"
- The genuinely hard part is **domain-hard, not coding-hard** — chart of accounts and classification rules require accounting expertise, not engineering
- **Audit-first UX**: tx hash → rule version → journal lines → override history

### Gemini (Architect) — Unique Contributions
- **Plaid analogy**: The parsing layer is really a "Categorization Engine", not just ABI decoding
- **Xero/QuickBooks "Reconciliation" pattern**: Don't aim for 100% automation — build a confirm/reject UI
- **Segment "Replay" capability**: Must be able to replay ledger generation over old data when parser logic changes
- **Chain reorganization handling**: Must only book finalized blocks or implement void-and-rebook for reorgs
- **K-IFRS requires KRW valuation evidence**: Korean tax authorities require proof of exchange rates used
- **Gas on failed transactions**: `getAssetTransfers` may filter these out — must handle separately
