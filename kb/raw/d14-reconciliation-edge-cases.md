---
id: d14-reconciliation-edge-cases
title: 대조 엔진 엣지케이스 (에이전트 연구 결과)
status: raw
tags: [mode/divergent, topic/risk, topic/architecture]
created: 2026-03-12
confidence: 0.65
---

## 핵심 발견 (배경 에이전트 연구 - 15개 중 상위)

### P0 (Phase 2 필수)
1. **Hash 정규화**: lowercase + 0x prefix. DB CHECK 제약 가능.
   → d7-txhash-normalization과 일치. 에이전트가 독립 확인.
2. **Fee 분리 비교**: value_decimal만으로 매칭, fee_decimal은 별도 카테고리.
   서비스 DB가 gross/net 중 어느 것을 저장하는지에 따라 매칭 로직 분기.
3. **소수점 정밀도**: value_raw(STRING) 또는 NUMERIC(38,18) 비교. Float/Double 사용 금지.
   → EC-4와 일치.
4. **확정 블록만 대조**: safeBlock 이내만 대조 대상. 미확정 블록은 제외.

### P1 (Phase 2 권장)
5. **RBF/Nonce 교체**: 원본 tx_hash 사라지고 신규 tx_hash 생성.
   서비스 DB가 원본 기록 → ON_CHAIN_ONLY / DB_ONLY 오탐 가능.
   → raw_transactions에 replaced_by_tx_hash nullable 컬럼 고려.
6. **Batch TX 분할/병합**: 서비스 DB 1건 = 온체인 N건 (또는 반대).
   Fuzzy match: timestamp ±5분 + sum(amount) 일치 시 후보 제시.
7. **서비스 DB 미확정 기록**: broadcast 직후 기록 → 온체인 미확정.
   "24시간 이상 미확정" 별도 리포트 필요.
8. **방향(Direction) 모호성**: value 양수/음수, RECEIVED/SENT 구분 필요.

### P2 (Phase 2+ 검토)
9. **NULL/잘못된 tx_hash**: 서비스 DB placeholder ("PENDING_xxx") 처리.
10. **주소 형식 정규화**: EVM checksum vs lowercase, UTXO P2PKH/bech32.
11. **timestamp 오차**: block_timestamp vs 앱 서버 시간. ±5분 허용.
12. **비표준 ERC-20**: Transfer 이벤트 미발생 → d12와 연관.

## Phase 2 테스트 Golden Set 제안
- 100개 실제 메인넷 TX (Etherscan 검증)
- 5개 batch split, 5개 fee mismatch, 5개 precision edge, 5개 failed TX
- 서비스 DB 모의: 의도적 불일치 (누락, 잘못된 hash, 1 wei 차이)
