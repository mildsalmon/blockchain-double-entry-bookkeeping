I’ll evaluate the idea against the actual repository state first. I’m starting by mapping the project structure, dependencies, and current test surface in parallel, then I’ll inspect the core ingestion/classification/ledger code paths and produce a concrete feasibility assessment with file:line evidence.
Initial scan shows no source files at repository root, so I’m now enumerating hidden/workflow directories (`.omc`, `.dev`, `.claude`, `docs`) to find where the actual architecture artifacts live before evaluating complexity and feasibility.
I found no application code yet, only orchestration metadata. Next I’m checking `docs/` and git history to confirm whether this repo currently contains only concept/planning artifacts or if source files were removed.
I confirmed there is no implementation yet (no commits, no dependency manifests, no tests). I’m reading the local project metadata next to ground the feasibility assessment in what actually exists in this repo right now.
I now have enough repository context: this is currently a pre-implementation workspace with only prompt/state artifacts. I’m briefly loading the relevant local skill guidance (`tech-decision` and `using-superpowers`) so the final evaluation follows your repo’s expected decision-analysis workflow.
**Summary**
The idea is technically feasible for an MVP, but only with strict scope control. The genuinely hard part is not chain ingestion; it is translating one on-chain transaction into auditable, K-IFRS-aligned accounting events with valuation and overrides (`.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:73`, `.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:77`). This repo is still in ideation with no implementation/test stack yet, so complexity is from-scratch (`.dev/state.json:2`, `.omc/project-memory.json:6`, `.omc/project-memory.json:13`).

**Analysis**

1. **Implementation complexity: hard vs straightforward**
- **Hard (core risk)**
- Transaction interpretation/rule engine (M:N mapping + open-ended DeFi) is the hardest subsystem (`.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:73`, `.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:74`).
- Cost basis + price sourcing is second hardest due unreliable long-tail token pricing (`.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:69`, `.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:75`).
- Chart-of-accounts standardization and audit/override workflow are domain-hard, not coding-hard (`.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:76`, `.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:77`).
- **Straightforward**
- Ethereum transfer ingestion via Alchemy API is implementation-straightforward (`.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:66`).
- Basic journal UI for review/edit is straightforward CRUD (`.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:70`).
- Double-entry posting itself is straightforward relative to classification (library or custom), because posting is deterministic once events are classified (`.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:68`).

2. **Technical feasibility (MVP)**
- **Yes, feasible** if MVP scope is narrowed to a small taxonomy (e.g., ETH/ERC-20 transfers + fees + 1-2 common swap patterns) and excludes “infinite DeFi” coverage (`.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:67`, `.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:74`).
- **Not feasible** as an MVP if “swaps, staking, etc.” implies broad protocol completeness from day one (`.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:67`).
- Confidence note: this repo has no current code/dependencies/tests, so this is architecture-level feasibility, not implementation validation (`.omc/project-memory.json:6`, `.omc/project-memory.json:8`, `.omc/project-memory.json:21`).

3. **Alternative approaches / better starting points**
- Start as an **“audit workbench + export”** (draft journal outputs + evidence links) before full accounting system ownership; this directly addresses auditability need with lower scope (`.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:77`).
- Use a **plugin-based classifier** (protocol adapters) rather than one giant universal classifier, to contain DeFi scope explosion (`.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:74`).
- Prefer an **event-sourced architecture** (raw chain events immutable, accounting interpretations versioned) to support reclassification and audit trail (`.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:73`, `.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:77`).

**Root Cause**
The dominant architectural difficulty is a **semantic gap**: blockchain transfer/event data does not directly encode accounting intent, and one tx can map to multiple accounting events requiring valuation and policy judgment (`.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:61`, `.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:73`, `.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:75`).

**Recommendations (prioritized)**
1. Build an immutable `raw_event` layer + versioned `classification_result` layer + `journal_entry` layer.  
Effort: Medium | Impact: High.
2. Scope MVP to fixed transaction archetypes only; defer long-tail DeFi protocols.  
Effort: Low | Impact: High.
3. Implement price-source provenance + confidence score + manual override with reason codes.  
Effort: Medium | Impact: High.
4. Start with existing ledger engine behind an internal interface (swap later if needed).  
Effort: Low | Impact: Medium.
5. Ship audit-first UX (trace tx hash -> rule version -> journal lines -> override history).  
Effort: Medium | Impact: High.

**Trade-offs**

| Recommendation | Benefit | Cost |
|---|---|---|
| Event-sourced pipeline | Reprocessing, auditability, deterministic history | More schema/infra upfront |
| Tight MVP scope | Faster delivery, lower model risk | Limited protocol coverage initially |
| Price provenance + overrides | Better accountant trust, defensibility | More UI/workflow complexity |
| Library-backed ledger core first | Faster time-to-market | Possible future migration constraints |
| Plugin-based DeFi adapters | Bounded complexity per protocol | More moving parts in adapter lifecycle |

**References**
- `.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:61` problem statement
- `.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:63` proposed solution and target users
- `.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:66` ingestion via Alchemy
- `.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:67` classification scope (ETH/ERC20/swaps/staking)
- `.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:68` ledger engine choice
- `.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:69` FIFO cost basis
- `.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:70` frontend edit/view requirement
- `.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:73` M:N tx interpretation risk
- `.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:74` infinite DeFi scope risk
- `.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:75` price reliability risk
- `.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:76` chart-of-accounts standard risk
- `.omc/prompts/codex-prompt-evaluate-this-idea-for-a-blockchain-double-entry-b-632d15f9.md:77` audit trail/manual override requirement
- `.dev/state.json:2` project is in brainstorm stage
- `.omc/project-memory.json:6` no language stack identified
- `.omc/project-memory.json:8` no package manager identified
- `.omc/project-memory.json:13` no test command configured
- `.omc/project-memory.json:21` no test pattern configured