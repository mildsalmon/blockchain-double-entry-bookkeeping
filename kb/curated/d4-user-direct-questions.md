---
id: d4-user-direct-questions
title: "D4: 사용자 직접 질문 답변 정리"
status: curated
confidence: 0.85
tags: [topic/blockchain, topic/product]
created: 2026-03-11
---

## Q1: 스마트 컨트랙트가 EOA에 직접 물량을 뿌리는 일이 빈번할까?

**답변: 거래소 운영에서는 드물다. 사용자의 직관이 맞다.**

거래소 입출금 구조:
- 입금: 고객 → 개별 입금 주소(EOA) → sweep → 핫월렛(EOA). 전부 EOA→EOA.
- 출금: 핫월렛(EOA) → 고객 주소. EOA→EOA.
- 콜드→핫 보충: 대부분 EOA→EOA. **예외: Gnosis Safe(멀티시그) → 핫월렛 = Internal TX**

"스마트 컨트랙트도 별도 wallet에 물량을 모아두고 뿌리는 거 아닌가?"
→ **맞다.** 대부분의 거래소는 스마트 컨트랙트에서 직접 분배하지 않고, 
  컨트랙트 → 중간 EOA → 최종 배포 구조를 사용한다.

Internal TX가 실제로 문제되는 유일한 케이스:
- 콜드월렛이 멀티시그 컨트랙트(Gnosis Safe)인 경우의 핫월렛 보충
- 이 경우에도 금액이 크므로 잔고 대사에서 즉시 감지

## Q2: UTXO listsinceblock public API에서 사용 가능한가?

**답변: 불가능. Bitcoin Core의 wallet RPC이다.**

- `listsinceblock`은 Bitcoin Core 풀노드의 wallet RPC
- 전제조건: (1) 자체 노드 운영 (2) importaddress로 watch-only 등록
- Blockstream API, Blockcypher, Mempool.space 등 public API에서는 미제공

대안:
- 자체 노드: getblock(hash, 2) verbosity=2 블록순회 + 주소 필터링 (importaddress 불필요)
- Public API: Blockstream GET /api/address/{addr}/txs (rate limit ~10 req/s)
- PRD 수정 제안: "수집 방식"을 getblock 블록순회로 기본, listsinceblock은 최적화 옵션

## Q3: 5.3과 5.4 사이에 Phase 3가 빠진 것 같다

**답변: 실제로 빠지지 않았다. 번호 혼동.**

현재 PRD 구조: 5.3(대조 결과, Phase 2) → 5.4(UTXO+알림, Phase 3) → 5.5(Phase 4 예약)
Phase 3 데이터 모델은 5.4에 있다. 다만 5.3의 서브섹션(5.3.1~5.3.5)이 깊어서 5.4와의 경계가 불명확.
수정 제안: Phase별 접두사를 소제목에 명시. "5.3 [Phase 2] 대조 결과", "5.4 [Phase 3] UTXO+알림"
