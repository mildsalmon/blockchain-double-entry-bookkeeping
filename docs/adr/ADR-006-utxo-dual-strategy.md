# ADR-006: UTXO 체인 이중 수집 전략

## 상태: 확정 (Phase 3)

## 맥락

BTC/DOGE는 EVM과 달리 UTXO 모델을 사용한다.
TX가 N:M 입출력 구조이며, 잔고 조회 방식도 상이하다.
또한 자체 노드 보유 여부에 따라 사용 가능한 RPC가 달라진다.

## 결정

**환경에 따른 이중 전략을 채택한다.**

| 환경 | 수집 방식 | 장점 | 단점 |
|------|----------|------|------|
| 자체 노드 | `listsinceblock` (Bitcoin Core RPC) | 효율적, 증분 조회 내장 | 노드 운영 필요 |
| Public API | `getblock(hash, verbosity=2)` | 노드 불필요 | 블록 단위 순회 필요, Rate limit |

## Public API 제약

- Blockstream, Blockcypher 등 Public API는 `listsinceblock`을 지원하지 않음
- `listsinceblock`은 Bitcoin Core 전용 RPC 명령
- Public API 사용 시 블록 단위 순회 + 주소 필터링으로 대체

## UTXO 데이터 모델

- `raw_transactions.raw_data` JSONB에 `vin[]`, `vout[]` 전체 보존
- 수수료: 명시 필드 없음, `sum(vin.value) - sum(vout.value)`로 계산
- `vin[].value`는 노드 버전에 따라 미제공 가능 → 이전 TX 조회로 보완

### 다중 주소 문제 (N:M vin/vout)

EVM TX는 from 1개, to 1개로 단순하다.
BTC TX는 다르다. 입력(vin) N개, 출력(vout) M개가 하나의 TX에 묶인다.

#### 입금 예시

감시 월렛 `bc1q_MY`로 0.7 BTC가 입금되는 TX:

```
[입력 — 누가 보냈나]              [출력 — 누가 받았나]
vin[0]: addr_A  0.5 BTC  ──┐
                            ├──→  vout[0]: bc1q_MY  0.7 BTC  ← 감시 월렛 입금
vin[1]: addr_B  0.3 BTC  ──┘
                            └──→  vout[1]: addr_C   0.09 BTC   (잔돈)
                                  (수수료: 0.01 BTC = 입력합 - 출력합)
```

- 보낸 사람이 2명(addr_A, addr_B)이지만 **TX는 1개** (tx_hash 1개)
- `from_address`에 누굴 넣어야 하나? → **대표값** 문제 발생

#### 출금 예시

감시 월렛 `bc1q_MY`에서 0.6 BTC를 출금하는 TX:

```
[입력]                              [출력]
vin[0]: bc1q_MY  1.0 BTC  ──┐
        (감시 월렛)          ├──→  vout[0]: addr_외부  0.6 BTC  (출금)
                             └──→  vout[1]: bc1q_MY   0.39 BTC (거스름돈)
                                   (수수료: 0.01 BTC)
```

- 거스름돈(change output)이 감시 월렛으로 돌아옴
- `to_address`에 감시 월렛? 외부 주소? → **대표값** 문제 발생

#### 저장 전략

`raw_transactions` 테이블에는 **감시 월렛 관점의 요약값**을 저장한다.
정확한 원본은 항상 `raw_data` JSONB에 보존된다.

**입금 TX** (감시 월렛이 vout에 있는 경우):

| 필드 | 값 | 근거 |
|------|-----|------|
| `from_address` | `vin[0].address` (addr_A) | 첫 번째 입력 주소를 대표값으로 |
| `to_address` | `bc1q_MY` (감시 월렛) | 감시 월렛이 수신자 |
| `value_raw` | `0.7 BTC` | 감시 월렛이 받은 vout의 합계 |

**출금 TX** (감시 월렛이 vin에 있는 경우):

| 필드 | 값 | 근거 |
|------|-----|------|
| `from_address` | `bc1q_MY` (감시 월렛) | 감시 월렛이 송신자 |
| `to_address` | `addr_외부` | 감시 월렛이 **아닌** 첫 번째 vout 주소 (거스름돈 제외) |
| `value_raw` | `0.6 BTC` | 감시 월렛이 **아닌** vout의 합계 |

**공통**:

| 필드 | 값 |
|------|-----|
| `raw_data` | `vin[]`, `vout[]` 전체 JSONB 원본 |

#### 왜 이렇게 하나?

`from_address`/`to_address`/`value_raw`는 **TX 목록 화면용 요약값**이다.
재무팀이 한눈에 "누가 보냈고, 누가 받았고, 얼마인지"를 파악하기 위한 것.

정밀 분석(대조, 잔고 역산)은 반드시 `raw_data`의 `vin[]`/`vout[]`을 파싱한다.
Phase 2 대조 엔진도 `raw_data` 기반으로 금액을 계산한다.

### UTXO 기반 잔고 역산 (cutoff 이후)

UTXO 체인에서 cutoff_balance + 이후 수집된 TX로 잔고를 역산할 수 있다:

```
balance(t) = cutoff_balance
             + sum(수신 vout.value, block > cutoff_block, block <= t)
             - sum(소비 vin.value, block > cutoff_block, block <= t)
```

cutoff 이전 TX는 수집하지 않으므로, cutoff 이전 시점의 잔고는 역산 불가.
cutoff 이후 시점은 수집된 TX만으로 정확한 잔고 계산이 가능하다.

## 거부한 대안

- **Public API만 지원**: 자체 노드 보유 시 `listsinceblock`의 효율성 포기
- **자체 노드만 지원**: 노드 미보유 환경에서 사용 불가
- **UTXO 전용 테이블**: 별도 테이블 없이 `raw_data` JSONB로 충분

## `listsinceblock` 전제조건

- **자체 풀노드 필수**: Blockstream, Blockcypher 등 Public API는 Bitcoin Core wallet RPC를 제공하지 않음
- **`importaddress` 등록 필수**: 감시 대상 주소를 노드의 wallet에 watch-only로 등록해야 함
  - `rescan=true` (기본): 전체 블록체인 리스캔 → **노드 수시간 응답 불가**
  - `rescan=false`: 리스캔 스킵, 과거 TX 미감지. cutoff 방식이면 과거 TX 불필요이므로 충분할 수 있음
  - Phase 3 설계 시 `rescan` 옵션 결정 필요
- **DOGE 참고**: Dogecoin Core도 `listsinceblock` 지원하나, descriptor wallet 미지원. `importaddress` 방식만 가능

## DOGE 블록 순회 부하

- BTC: 10분/블록 → 하루 144블록
- DOGE: 1분/블록 → **하루 1440블록** (BTC의 10배)
- Public API 폴백 시 rate limit 전략 필수 (Blockstream은 DOGE 미지원)

## 현황 (2026-03-12 확인)

- **BTC/DOGE 자체 노드 보유 확인** → `listsinceblock` 사용 가능
- 기타 UTXO 체인(LTC 등)은 노드 미확인 → Public API 폴백 필요
- **이중 전략 유지**: 자체 노드 있으면 `listsinceblock`, 없으면 블록 순회

## Phase 3 사전 준비 (인프라팀 조율)

- Phase 2 중반에 인프라팀에 `importaddress` 실행 요청
- `rescan=false` 가능 여부 확인 (cutoff 방식이면 과거 TX 불필요)
- DOGE용 Public API 대안 조사 (SoChain 등)

## 재검토 조건

- 기타 UTXO 체인 추가 시 노드 보유 여부 확인
- Public API rate limit이 운영에 부적합하면 노드 확보 우선
