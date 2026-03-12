---
id: a4-product-manager
title: 프로덕트 매니저 의견
status: raw
confidence: 0.85
tags: [topic/product, topic/risk]
created: 2026-03-11
---

## 핵심 포지션

| # | 주제 | 입장 |
|---|------|------|
| 1 | Excel | **조건부 찬성** (한글인코딩, 감사증빙, 시트분리 = 재무팀 필수) |
| 2 | 헥사고날 | 찬성 |
| 3 | Internal TX | 조건부 (UI에 명시적 안내 필수) |
| 4 | PRD 섹션 | 찬성 |
| 5 | listsinceblock | 반대(현 시점) - Phase 3 유지 |
| 6 | MySQL | 찬성 (Phase 2 선행 작업) |
| 7 | Atlas | **반대** |
| 8 | Cutoff | **조건부 찬성 + 수동 백필(Backfill) 제안** |

## 주요 인사이트 (새로운 관점)
- D1: 재무팀 반드시 과거 데이터 요청함. "어제 등록한 지갑의 지난달 TX" = 거의 확실
- D2: 수동 백필 3단계: (1) 기본 cutoff (2) POST /api/wallets/{id}/backfill (3) UI 배너
- D3: 백필은 별도 스레드, 정상 동기화 방해 금지
- D4: "수집 안된" vs "TX가 없는" 상태 구분 필수
- D5: CSV 한글 인코딩 = Windows Excel에서 UTF-8 BOM 없으면 깨짐. 비전문가에게 안내 불가능
- D6: 감사인 제출 자료 = 사실상 Excel. tx_hash가 지수표기로 변환되는 문제
