# AGENTS.md

Codex-first harness guide for this repository.

## Skill Root

- Skills: `.codex/skills`
- Agents: `.codex/agents`
- Scripts: `.codex/scripts`

## Available Skills

- `discuss` - Socratic pre-planning discussion (`.codex/skills/discuss/SKILL.md`)
- `specify` - Interview-driven plan creation (`.codex/skills/specify/SKILL.md`)
- `open` - Draft PR creation from spec (`.codex/skills/open/SKILL.md`)
- `execute` - Orchestrator-worker implementation (`.codex/skills/execute/SKILL.md`)
- `publish` - Draft PR to ready (`.codex/skills/publish/SKILL.md`)
- `compound` - Learning extraction from completed work (`.codex/skills/compound/SKILL.md`)
- `bugfix` - Root-cause one-shot bugfix (`.codex/skills/bugfix/SKILL.md`)
- `tech-decision` - A/B comparative analysis (`.codex/skills/tech-decision/SKILL.md`)
- `tribunal` - 3-perspective adversarial review (`.codex/skills/tribunal/SKILL.md`)
- `reference-seek` - Internal/external implementation reference search (`.codex/skills/reference-seek/SKILL.md`)
- `dev-scan` - Community opinion scan (`.codex/skills/dev-scan/SKILL.md`)
- `deep-research` - Multi-source deep research (`.codex/skills/deep-research/SKILL.md`)
- `state` - PR state tracking (`.codex/skills/state/SKILL.md`)
- `init` - Worktree config bootstrap (`.codex/skills/init/SKILL.md`)
- `worktree` - Worktree lifecycle management (`.codex/skills/worktree/SKILL.md`)
- `ultrawork` - End-to-end sequential orchestrator (`.codex/skills/ultrawork/SKILL.md`)
- `skill-session-analyzer` - Post-hoc session analysis (`.codex/skills/skill-session-analyzer/SKILL.md`)

## Trigger Rules

1. If the user explicitly names a skill (`/skill`, `$skill`, or plain name), use that skill.
2. If a request clearly matches one skill, use that skill flow instead of ad-hoc behavior.
3. If multiple skills apply, use the minimal set and state the execution order in one line.
4. If a named skill path is missing/unreadable, state it briefly and continue with best fallback.

## Skill Execution Rules

1. Open only the needed parts of `SKILL.md` first (progressive disclosure).
2. Resolve relative paths from the skill directory (for example `.codex/skills/<name>/`).
3. Load referenced files lazily; do not bulk-read entire `references/` trees.
4. Prefer provided scripts/templates/assets over rewriting from scratch.
5. Keep context compact; summarize large references instead of pasting long excerpts.

## Project Conventions

- Keep planning artifacts under `.dev/specs/{name}/`.
- Use `/init` then `/worktree` (or `taco`) for worktree lifecycle.
- Prefer Codex-native execution; legacy Claude assets are not part of the default flow.

