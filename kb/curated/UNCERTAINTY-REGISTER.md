---
id: uncertainty-register
title: Uncertainty Register
status: curated
tags: [type/register]
created: 2026-03-11
---

| ID | Item | Type | Likelihood | Impact | Next Test |
|----|------|------|-----------|--------|-----------|
| ~~U1~~ | ~~Atlas vs Flyway~~ | ~~해소~~ | - | - | Flyway 유지 확정 (d1) |
| ~~U2~~ | ~~헥사고날 범위~~ | ~~해소~~ | - | - | "포트/어댑터" 명명 확정 (d2) |
| ~~U3~~ | ~~백필 스코프 크립~~ | ~~해소~~ | - | - | Phase 2+ 이연, 하드 경계 설정 (d3) |
| U4 | BTC cutoff 잔고 정확도 | epistemic | MED | MED | Phase 3 진입 시 테스트 |
| U5 | Exchange DB 플랫폼+스키마 미확정 (MySQL/Athena/Databricks/Iceberg) | epistemic | HIGH | HIGH | 사용자가 스키마 제공 시 ADR-004 갱신 |
| U6 | Exchange DB 쿼리 호환성 (SQL 방언 차이) | epistemic | MED | MED | Phase 2 어댑터 개발 시 검증 |
| ~~U7~~ | ~~자체 BTC/DOGE 노드 보유 여부~~ | ~~해소~~ | - | - | BTC/DOGE 노드 있음. 기타 UTXO 체인은 미확인 → 이중 전략 유지 |
| U8 | RPC 엔드포인트 안정성 (public vs 자체) | epistemic | MED | HIGH | Phase 1b 실제 수집 시 측정 |
| ~~U9~~ | ~~콜드월렛 유형 (EOA vs Gnosis Safe/멀티시그)~~ | ~~해소~~ | - | - | EOA 확인. Internal TX 리스크 낮음, Phase 4 trace API 우선순위 하향 |
| U10 | 주 DB 확정 여부 (PostgreSQL vs File/Parquet) | epistemic | MED | MED | 현재 PostgreSQL 기준 설계, 변경 가능성 있음 |
