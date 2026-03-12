---
id: d13-reorg-handling-strategy
title: Reorg 처리 전략 (에이전트 연구 결과)
status: raw
tags: [mode/divergent, topic/blockchain, topic/architecture]
created: 2026-03-12
confidence: 0.7
sources: [tatum docs, quicknode docs, polygon.technology, ethereum.org]
---

## 핵심 발견 (배경 에이전트 연구)

### 1. 현재 confirmation blocks 마진은 충분
| 체인 | 설정 | 최대 관측 reorg | 안전성 |
|------|------|---------------|--------|
| ETH | 12 | 7 (2022.05) | 충분 |
| POL | 128 | 2 (Heimdall v2 이후) | 과잉 (하지만 안전) |
| BSC | 15 | <5 | 충분 |
| BTC | 6 | 1-2 (일상), 6 (안전) | 충분 |
| DOGE | 40 | 미추적 | 합리적 |

### 2. REORGED 상태값 추가 제안
- raw_transactions.tx_status에 `REORGED` 추가 (CONFIRMED, FAILED 외)
- reorg 감지 시 DELETE 대신 UPDATE SET tx_status='REORGED'
- 감사 추적 보존, UI에서 기본 필터 아웃

### 3. Soft vs Hard reorg 구분
- Soft (<10블록): 자동 rewind + rescan
- Hard (>confirmationBlocks): 관리자 알림, 동기화 일시 중지, 수동 검토

### 4. Polygon checkpoint finality
- L1 체크포인트 확정 시 POL TX는 되돌릴 수 없음
- Phase 2+에서 checkpoint-aware 모드 검토 가능

### 5. 구현 제안 (Phase 1b)
- sync 시 parent hash 검증 (last_block_hash 비교) — RPC 1회 추가 호출
- hash 불일치 시 reorg_depth 계산 → soft/hard 분기
- 모니터링: reorg_detections_count, avg_reorg_depth 메트릭
