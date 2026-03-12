---
provider: "gemini"
agent_role: "architect"
model: "gemini-3-pro-preview"
prompt_id: "be793371"
timestamp: "2026-02-22T06:51:01.991Z"
---

## Summary
The proposed architecture correctly identifies the core problem (blockchain data ≠ accounting data) but underestimates the complexity of **data normalization** and **historical valuation**. While `medici` provides a solid double-entry foundation, the critical failure point will likely be the "Parsing" layer's inability to automatically classify complex DeFi transactions without a robust, maintainable strategy for protocol-specific adapters.

## Analysis
### 1. Data Ingestion & Parsing (The "ETL" Layer)
-   **Component:** `Alchemy getAssetTransfers` / `viem`
-   **Risk:** Relying solely on transfer events misses state changes that affect value but don't move tokens (e.g., staking rewards that accrue in-contract, rebasing tokens, or complex DeFi position updates).
-   **Gap:** "Parsing" is described as ABI decoding, but accounting requires *semantic* understanding. Knowing a function was `swap(a, b)` is useless without knowing the *historical USD value* of `a` and `b` at block `N`.

### 2. The Ledger (The "Source of Truth")
-   **Component:** `medici` (MongoDB) vs Custom PostgreSQL
-   **Risk:** `medici` is excellent for double-entry enforcement but lacks native support for **multi-asset cost basis tracking** (FIFO/LIFO layers). You will fight the library to implement K-IFRS specific inventory matching logic.
-   **Gap:** K-IFRS requires strict separation of "Gas Expense" vs "Asset Cost Basis". A standard ledger entry `Dr Gas Expense / Cr ETH` is simple, but `Dr Asset / Cr ETH` (swap) requires calculating the realized gain/loss on the ETH spent, which is a recursive problem.

### 3. Korean Market Specifics
-   **Component:** Reporting / Export
-   **Risk:** K-IFRS compliance is not just about the ledger; it's about the *evidence*. Korean tax authorities often require proof of exchange rates used.
-   **Gap:** No mention of a "Price Oracle" or "Valuation Engine" to stamp every transaction with a KRW value at the time of execution.

## Root Cause (Potential Failure)
The architectural root cause of failure for similar projects is **tight coupling between the Ingestion and Ledger layers**.
If you pipe Alchemy data directly into Medici, you cannot easily re-classify or re-value transactions when (not if) the accounting rules or price data change. You need an intermediate "Normalized Transaction" state.

## Recommendations

1.  **Implement a "Valuation Engine" Service** - [High Effort] - [Critical Impact]
    -   **Why:** You cannot do accounting without fiat values.
    -   **Action:** Build a service that takes `(Token, BlockHeight)` and returns `KRW_Value`. Cache this aggressively.
    -   **Trade-off:** High initial complexity vs. impossible-to-fix data later.

2.  **Adopt a "Three-Layer" Architecture** - [Medium Effort] - [High Impact]
    -   **Layer 1 (Raw):** Immutable JSON dump of the transaction/receipt (Alchemy response).
    -   **Layer 2 (Normalized):** A generic "Event" (e.g., `Incoming(Asset, Amount, Sender)`).
    -   **Layer 3 (Ledger):** The double-entry records derived from Layer 2.
    -   **Why:** Allows re-running accounting logic (Layer 3) without re-fetching blockchain data (Layer 1).

3.  **Use PostgreSQL over MongoDB (Medici)** - [High Effort] - [Medium Impact]
    -   **Why:** Financial data is relational. Complex queries for "Holdings at Block N" or "FIFO stack for Token X" are significantly harder and slower in Mongo.
    -   **Action:** Use a SQL-based double-entry schema (e.g., `entries` table with `transaction_id`, `account_id`, `debit`, `credit`).

## Trade-offs

| Option | Pros | Cons |
| :--- | :--- | :--- |
| **Medici (Mongo)** | Fast setup, built-in double-entry logic. | Hard to query complex relationships (FIFO stacks), weak ACID guarantees compared to Postgres. |
| **Custom SQL Ledger** | Full control, relational integrity, complex reporting (SQL). | Higher initial dev time, must implement double-entry constraints manually. |
| **Alchemy Only** | Easy to start. | Misses internal transactions, specific DeFi states, and historical prices. |

## Similar Solutions (Non-Crypto) & Learnings
-   **Plaid (Fintech):** They don't just give raw bank data; they *categorize* it. Your "Parsing" layer needs to be a "Categorization Engine".
-   **Xero/QuickBooks:** They use a "Reconciliation" UI where users confirm the system's guess. **Do not aim for 100% automation.** Build a UI for "Review & Confirm".
-   **Segment (Data):** The "Replay" capability. You must be able to replay your ledger generation logic over old data when you fix a bug in your parser.

## Blind Spots
1.  **Reorg Handling:** Blockchains reorg. If you book a ledger entry for block `N` and it gets orphaned, your ledger is wrong. **Mitigation:** Only book "Finalized" blocks or implement a "Void & Re-book" logic for reorgs.
2.  **Spam/Dust:** Airdrops of scam tokens will clutter the ledger. **Mitigation:** Allow users to "Ignore/Burn" assets in the UI so they don't appear in reports.
3.  **Gas Fees on Failed Tx:** You still pay gas for failed transactions. These are expenses that must be booked even if no asset transfer occurred. `getAssetTransfers` might filter these out if not careful.