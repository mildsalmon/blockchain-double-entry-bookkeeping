---
id: d8-data-retention-strategy
title: raw_transactions 데이터 보존 전략
status: raw
tags: [mode/divergent, topic/devops, topic/architecture]
created: 2026-03-12
confidence: 0.25
---

## 제안 (DIVERGENT — SCALE SHIFT)

raw_transactions 테이블이 무한 성장할 수 있다.
5개 체인, 각 수십~수백 월렛의 TX가 5분마다 누적된다.
1년 운영 시 수천만~억 행 가능.

## 현재 상태

- PRD에 보존 정책 미명시
- raw_data JSONB는 행당 수 KB~수십 KB (UTXO vout 많은 경우)
- 인덱스: chain_id+block_number, chain_id+wallet_address, tx_hash, block_timestamp

## 잠재 문제

| 시점 | 예상 행 수 | 디스크 | 쿼리 성능 |
|------|----------|-------|----------|
| 1개월 | ~50만 | ~5GB | 양호 |
| 6개월 | ~300만 | ~30GB | 인덱스 의존 |
| 1년 | ~600만 | ~60GB | 파티셔닝 필요 가능 |

## 선택지 (Phase 2+ 검토)

1. **PostgreSQL 테이블 파티셔닝** (chain_id + block_timestamp 기준)
2. **오래된 데이터 콜드 스토리지 이동** (S3/GCS export)
3. **reconciliation 완료된 TX의 raw_data 압축/제거**

## 관련성 검증

현재 Phase 1a이므로 즉시 조치 불필요.
그러나 테이블 설계 시 파티셔닝 친화적 구조(chain_id + timestamp 인덱스)는 이미 반영됨.
향후 이슈 발생 시 참조용으로 보존.
