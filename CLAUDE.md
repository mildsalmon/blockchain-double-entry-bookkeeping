# CLAUDE.md

모든 가이드라인은 `AGENTS.md`에 통합되어 있습니다.
Claude Code와 Codex 모두 `AGENTS.md`를 참조합니다.

상세 문서는 `docs/` 디렉토리를 참조하세요.

- `AGENTS.md` — AI Agent 공용 가이드라인 (원칙, 스코프, 컨벤션)
- `PRD.md` — 제품 요구사항 명세
- `docs/architecture.md` — 포트/어댑터 구조, SOLID 원칙
- `docs/schema-decisions.md` — DB 설계 결정사항
- `docs/chain-adapters.md` — 체인별 수집 전략
- `docs/known-edge-cases.md` — 알려진 엣지케이스
- `docs/adr/` — Architecture Decision Records (ADR-001~008)

## 프로젝트 비활성 스킬

이 프로젝트에서 아래 스킬은 사용하지 마라:
- `tutor`, `tutor-setup` — 학습 도구, 이 프로젝트와 무관
