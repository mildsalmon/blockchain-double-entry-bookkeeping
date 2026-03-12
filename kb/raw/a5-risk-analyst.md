---
id: a5-risk-analyst
title: 리스크 분석가 의견
status: raw
confidence: 0.85
tags: [topic/risk, topic/blockchain]
created: 2026-03-11
---

## 핵심 포지션

| # | 주제 | 입장 | 리스크 |
|---|------|------|--------|
| 1 | Excel | 조건부 | LOW - 체크섬(SHA-256) 기록 필요 |
| 2 | 헥사고날 | 찬성 | LOW |
| 3 | Internal TX | 조건부 | **HIGH** - 잔고대사=탐지O, 귀인(attribution)=X |
| 4 | PRD 섹션 | 찬성 | LOW |
| 5 | listsinceblock | 찬성 | MED - 이중옵션 |
| 6 | MySQL | 조건부 | **HIGH** - Read Replica+RO계정+TLS 필수 |
| 7 | Atlas | **반대** | MED |
| 8 | Cutoff | 조건부 | **HIGH** (최대 리스크) - 면책조항+backfill+커버리지표시 |

## 주요 인사이트 (새로운 관점)
- D1: Cutoff = 감사 완전성 결함. 2024년부터 운영중인 거래소가 2026년에 도입하면 2년치 TX 미검증
- D2: coverage_start_block vs registration_block 컬럼 분리 제안
- D3: 내보내기 시 SHA-256 체크섬을 reconciliation_runs에 기록
- D4: Internal TX 잔고 대사 한계 - "탐지O, 귀인X" (어떤 TX가 차이 유발했는지 특정 불가)
- D5: Exchange DB - @Transactional(readOnly=true)만으로 불충분, DB계정 수준 권한 필수
- D6: 이전 프로젝트 CutoffSnapshotService의 교훈 - cutoff 이전 TX 누락은 보정 불가, 무한 하드닝의 원인
