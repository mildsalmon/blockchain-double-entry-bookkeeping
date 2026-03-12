---
id: a2-blockchain-expert
title: 블록체인 도메인 전문가 의견
status: raw
confidence: 0.85
tags: [topic/blockchain, topic/architecture]
created: 2026-03-11
---

## 핵심 포지션

| # | 주제 | 입장 | 핵심 근거 |
|---|------|------|----------|
| 1 | Excel | 찬성 | Phase 2 배치 적절 |
| 2 | 헥사고날 | **찬성** | 인터페이스는 구현 2개 이상일 때만 |
| 3 | Internal TX | 조건부 | 멀티시그 콜드월렛이 핵심 리스크. 잔고 대사 Phase 1b 후반으로 앞당기기 제안 |
| 4 | PRD 섹션 | 찬성 | 단순 편집 오류 |
| 5 | listsinceblock | 조건부 | getblock(hash,2) 블록순회가 기본, listsinceblock은 최적화 옵션 |
| 6 | MySQL | 찬성 | JDBC 드라이버 + .env URL 변경 |
| 7 | Atlas | **반대** | Spring Boot Flyway 네이티브 통합 충분 |
| 8 | Cutoff | **찬성** | cutoff_balance + cutoff_block 컬럼 Phase 1a 추가 제안 |

## 주요 인사이트
- D1: Internal TX 실제 케이스 - Gnosis Safe(멀티시그) 실행→핫월렛이 가장 위험. 금액 크므로 잔고 대사에서 감지 가능
- D2: UTXO 전략 - getblock(hash,2) verbosity=2 블록순회 + 주소필터링이 기본. importaddress 불필요
- D3: BTC cutoff - Blockstream API chain_stats.funded_txo_sum - spent_txo_sum = 현재 잔고. 과거 시점 불가
- D4: Public API 옵션 - Blockstream(BTC), Blockcypher(BTC+DOGE), Mempool.space(BTC)
- D5: A1과 충돌 - 헥사고날 명시에 대해 A1은 반대, A2는 찬성(단 인터페이스 제한 조건부)
