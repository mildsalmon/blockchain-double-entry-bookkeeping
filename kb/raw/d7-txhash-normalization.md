---
id: d7-txhash-normalization
title: tx_hash 정규화 전략
status: raw
tags: [mode/divergent, topic/risk, topic/architecture]
created: 2026-03-12
confidence: 0.4
---

## 제안 (DIVERGENT — EDGE CASE)

대조 엔진(Phase 2)에서 tx_hash 매칭 시 정규화 필요성 검토.

## 잠재 문제

| 시나리오 | 예시 | 발생 가능성 |
|---------|------|-----------|
| 0x prefix 유무 | `0xabc...` vs `abc...` | 중간 (서비스 DB가 prefix 없이 저장할 수 있음) |
| 대소문자 차이 | `0xABC...` vs `0xabc...` | 높음 (EVM tx_hash는 case-insensitive) |
| UTXO txid 형식 | big-endian vs little-endian | 낮음 (대부분 big-endian) |

## 제안: 저장 시 정규화

- EVM: lowercase, 0x prefix 포함
- UTXO: lowercase, prefix 없음
- 대조 시: 양쪽 모두 동일 정규화 후 비교

## 관련성 검증

Phase 2 대조 엔진의 매칭 정확도에 직접 영향.
PRD 5.3.3 match_status 분류에서 false positive/negative 방지.
