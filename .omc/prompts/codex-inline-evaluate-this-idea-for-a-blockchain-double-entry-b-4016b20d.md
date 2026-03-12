Evaluate this idea for a blockchain double-entry bookkeeping web application:

**Problem**: DAOs and crypto projects need transparent financial audits, but blockchain transactions don't map cleanly to traditional accounting formats.

**Solution**: A web app that reads Ethereum blockchain data for specific wallets and generates double-entry bookkeeping ledgers (journal entries with debits/credits). Target users are accountants and tax professionals (Korean market, K-IFRS).

**Key Technical Components**:
1. Ethereum data ingestion via Alchemy getAssetTransfers API
2. Transaction classification engine (ETH transfers, ERC-20, swaps, staking, etc.)
3. Double-entry ledger generation using medici (Node.js) or custom engine
4. Cost basis tracking (FIFO)
5. Web frontend for viewing/editing journal entries

**Known Risks**:
- Transaction interpretation is M:N mapping (one tx → many accounting events)
- DeFi protocol support is infinite scope
- Historical price data is unreliable for long-tail tokens
- No established chart of accounts standard for crypto
- Accountants need full audit trail and manual override capability

Evaluate:
1. Implementation complexity — what's genuinely hard vs. straightforward?
2. Technical feasibility — can an MVP be built with reasonable effort?
3. Alternative approaches — are there better architectures or starting points?

Be specific and concise.