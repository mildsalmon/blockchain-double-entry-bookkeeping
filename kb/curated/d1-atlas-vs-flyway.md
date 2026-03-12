---
id: d1-atlas-vs-flyway
title: "D1: Atlas vs Flyway 의사결정"
status: curated
confidence: 0.9
tags: [topic/migration, mode/convergent]
created: 2026-03-11
---

## 결정: Flyway 유지 (추천 A)

### 근거 (E1~E3)
- E1: Atlas의 핵심 가치(drift detection, 선언적 관리, lint)는 규모 문제 해결용. 테이블 4개에서 해당 문제 미발생.
- E2: 전환 비용 비대칭. Flyway→Atlas는 baseline 1줄. Atlas→Flyway는 매우 어려움. 옵션 가치(optionality) = Flyway.
- E3: 도구 전환 1~2일 = Phase 1a 핵심 목표와 경쟁. CLAUDE.md 스코프 크립 체크리스트 #5, #6 해당.

### 기각된 대안
- B(지금 Atlas): Spring Boot auto-config 이탈, entrypoint 재설계, CLAUDE.md 전면 재작성
- C(Flyway+Atlas부분차용): 가능하지만 현 규모에서 실익 없음
- D(나중에 Atlas): 유효. Phase 2 이후 테이블 20+ 시점에 재검토

### 사용자 요청과의 충돌
사용자가 "atlas 사용하자"고 명시. 6명 분석(5 에이전트 + first-principles) 전원 반대.
핵심 논점: "존재하지 않는 문제에 대한 해결책 도입 = 스코프 크립"
제안: 사용자에게 근거 제시 후 판단 위임
