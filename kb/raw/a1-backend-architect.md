---
id: a1-backend-architect
title: 백엔드 아키텍트 의견
status: raw
confidence: 0.8
tags: [topic/architecture, topic/migration]
created: 2026-03-11
---

## 핵심 포지션

| # | 주제 | 입장 | 핵심 근거 |
|---|------|------|----------|
| 1 | Excel | 조건부 찬성 | PRD 따르되 CLAUDE.md 동기화 필요 |
| 2 | 헥사고날 | **반대** | 이 규모에서 오버엔지니어링. "포트/어댑터 기반" 수준으로 |
| 3 | Internal TX | 조건부 | 잔고 대사로 간접 감지 전략 적절 |
| 4 | PRD 섹션 | 찬성 | Phase별 접두사 통일 |
| 5 | listsinceblock | 반대(public 불가) | 이중 어댑터 전략 명시 필요 |
| 6 | MySQL | 찬성 | JdbcTemplate + 별도 DataSource, 분산TX 불필요 |
| 7 | Atlas vs Flyway | **강력 반대** | Spring Boot 자동구성 미지원, 테이블 4개에 전환비용 과대 |
| 8 | Cutoff | 찬성 | Phase 1b 첫 동기화 시 safeBlock 기준 잔고 스냅샷 |

## 주요 인사이트
- D1: Atlas 반대 - Spring Boot에서 Flyway는 1등 시민. Atlas는 외부 CLI 실행 필요, init container 파이프라인 구축 필요. 테이블 20개+ 이후 재검토
- D2: Exchange DB - JdbcTemplate으로 접근 (JPA 아님). 거래소 스키마 변경 시 SQL만 수정. TransactionManager도 분리
- D3: Cutoff - latest가 아닌 safeBlock 기준으로 eth_getBalance. Phase 1a에서 등록만, Phase 1b 첫 동기화 시 잔고 찍기
- D4: 헥사고날 - 순수 헥사고날이 아닌 "경량 포트-어댑터 구조". 공식 선언하면 리팩토링 요구 폭발
