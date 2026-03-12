# PRD: 멀티체인 TX 수집-대조 도구 (Chain Watcher)

> 버전: 1.0 | 작성일: 2026-03-11 | 상태: Draft

---

## 1. 개요

### 1.1 제품 정의

거래소가 보유한 핫/콜드 월렛의 **멀티체인 온체인 트랜잭션을 5~10분 주기로 수집**하고,
서비스 DB 기록과 대조하여 **누락/불일치를 자동 탐지**하는 내부 회계감사 도구.

### 1.2 핵심 가치

"온체인 진실(ground truth)"과 "서비스 DB 기록" 사이의 간극을 자동으로 발견하여,
재무팀이 불법자금 유입·미기록 tx·누락 출금을 일일 감사 수준으로 추적할 수 있게 한다.

### 1.3 배경

기존 `blockchain-double-entry-bookkeeping` 프로젝트는 복식부기·FIFO 원가추적·KRW 가격 환산 등
핵심 목표에 불필요한 기능이 과도하게 구현되었고, 정작 핵심인 "서비스 DB 대조" 기능이 없었다.
프로젝트를 리셋하고 본질에 집중한다.

---

## 2. 목표 및 비목표

### 2.1 목표

| 우선순위 | 목표 |
|---------|------|
| **P0** | 거래소 소유 월렛(핫/콜드)의 모든 온체인 TX를 수집 |
| **P1** | 수집된 TX와 서비스 DB를 대조하여 누락·불일치 탐지 |
| **P2** | UTXO 체인(BTC/DOGE) 수집 + 알림 시스템 |
| **P3** | Gas fee 집계, 특정 시점 잔고 역산, Internal TX |

### 2.2 비목표 (SCOPE BOUNDARY)

아래 항목은 이 프로젝트에서 **구현하지 않는다**:

| # | 금지 항목 | 이유 |
|---|----------|------|
| 1 | 복식부기 (차변/대변, 분개, 계정 체계) | 핵심 목표와 무관 |
| 2 | FIFO 원가 추적, 실현손익 계산 | 세무/회계 도구 영역 |
| 3 | 가격 조회/환산 (CoinGecko, KRW 등) | 불필요 |
| 4 | 인증/권한 시스템 | 블록체인 데이터 수정 불가, 불필요 |
| 5 | DEX 프로토콜 파싱 (Uniswap 등) | TX 수집에 불필요 |
| 6 | ERP/외부 시스템 연동 | 범위 밖 |
| 7 | 실시간 WebSocket 모니터링 | 5~10분 배치로 충분 |
| 8 | 멀티유저/다중 인스턴스 | 단일 배포 환경 |
| 9 | CI/CD 파이프라인 | 별도 요청 시만 |

---

## 3. 사용자 및 시나리오

### 3.1 주 사용자

**재무팀 오퍼레이터** (블록체인 비전문가, 웹 UI 사용)

### 3.2 핵심 워크플로우

#### 시나리오 A: 일일 감사 체크 (매일 아침)

1. 대시보드 접속 → 체인별 동기화 상태 신호등 확인 (초록/노랑/빨강)
2. 누락 의심 TX 건수 확인 (0건이면 정상)
3. 문제 있으면 상세 확인 → 조치

#### 시나리오 B: 서비스 DB 대조 (월말 결산)

1. 기간 선택 → 대조 실행
2. 결과: MATCHED / ON_CHAIN_ONLY / DB_ONLY / AMOUNT_MISMATCH / FAILED_TX
3. 불일치 항목 상세 확인 → CSV/Excel 내보내기

#### 시나리오 C: 잔고 확인 (Phase 4)

1. 날짜/시각 입력 → 해당 시점 온체인 잔고 조회
2. 서비스 DB 잔고와 비교

#### 시나리오 D: TX 검색 (고객 문의 대응, 낮은 우선순위)

1. tx hash 입력 → DB에 있는지 즉시 확인
2. 없으면 on-chain 조회 → 누락 원인 파악 (동기화 지연? internal tx?)
3. 조치: 재동기화 또는 수동 등록

---

## 4. 시스템 아키텍처

### 4.1 전체 구조

```
┌─────────────────────────────────────────────────────┐
│                  Spring Boot App                     │
│                                                      │
│  ┌──────────────────────────────────────────────┐    │
│  │         Central Batch Scheduler               │    │
│  │         (@Scheduled, 5min cycle)              │    │
│  └─────┬────┬────┬────┬────┬─────────────────────┘    │
│        │    │    │    │    │                          │
│   [ETH] [POL] [BSC] [BTC] [DOGE]                     │
│   sync  sync  sync  sync  sync                       │
│        │    │    │    │    │                          │
│  ┌─────▼────▼────▼────▼────▼─────────────────────┐    │
│  │     ChainAdapter (interface)                   │    │
│  │     ├── EvmChainAdapter (ETH/POL/BSC)         │    │
│  │     └── UtxoChainAdapter (BTC/DOGE)           │    │
│  └─────────────────┬─────────────────────────────┘    │
│                    │                                  │
│  ┌─────────────────▼─────────────────────────────┐    │
│  │     PostgreSQL (raw_transactions + sync)       │    │
│  └────────────────────────────────────────────────┘    │
│                                                      │
│  ┌────────────────────────────────────────────────┐    │
│  │     Next.js Frontend                           │    │
│  └────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
```

### 4.2 멀티체인 추상화

```
ChainAdapter (interface)
├── chainId: String
├── chainType: EVM | UTXO
├── getLatestBlockNumber(): Long
├── fetchTransactions(addresses, fromBlock, toBlock): List<NormalizedTx>
├── getConfirmationBlocks(): Int
└── isHealthy(): Boolean

EvmChainAdapter (abstract, ETH/POL/BSC 공통)
├── EthereumAdapter (chainId="ETH", confirmation=12)
├── PolygonAdapter (chainId="POL", confirmation=128)
└── BscAdapter (chainId="BSC", confirmation=15)

UtxoChainAdapter (abstract, BTC/DOGE 공통)
├── BitcoinAdapter (chainId="BTC", confirmation=6)
└── DogeAdapter (chainId="DOGE", confirmation=40)
```

**핵심: EVM 3개 체인은 RPC URL만 달라 99% 코드 공유.**

### 4.3 배치 스케줄러

- **Spring @Scheduled** (Quartz/Spring Batch는 현재 규모에 과도)
- 중앙 스케줄러가 체인별 독립 스레드에 작업 디스패치
- 체인 간 병렬 / 체인 내 순차 (같은 RPC에 과부하 방지)
- `sync_cursors` 테이블로 체인별 마지막 동기화 블록 추적
- 실패 시 다음 배치 주기에 자동 재시도 (fail_count 기반 백오프)

### 4.4 증분 동기화

```
매 배치:
  latestBlock = getLatestBlockNumber()
  safeBlock = latestBlock - confirmationBlocks  // reorg 안전 마진
  fromBlock = sync_cursors.last_synced_block + 1

  if fromBlock > safeBlock: skip (아직 확정 블록 없음)
  else: fetchTransactions(fromBlock, safeBlock) → save → update cursor
```

### 4.5 Internal Transaction 설명 및 전략

**사용자 질문: "A→B ETH 전송은 tx가 생기니까 감지 가능하지 않나?"**

**맞습니다 — 대부분의 경우에.** 단, 한 가지 예외가 있습니다:

| 유형 | 예시 | 감지 가능? |
|------|------|-----------|
| EOA→EOA ETH 전송 | A가 B에게 1 ETH 보냄 | **감지 가능** (블록 tx 목록에 있음) |
| ERC-20 Transfer | A가 B에게 USDC 보냄 | **감지 가능** (이벤트 로그로 감지) |
| Contract→EOA ETH 전송 | DEX가 A에게 ETH 반환 | **누락 가능** (internal tx) |

**비유**: 일반 tx는 "택배 기사가 직접 배달"하는 것. 블록의 배송 목록에 기록됩니다.
Internal tx는 "물류센터(스마트 컨트랙트)가 내부적으로 분배"하는 것. 블록의 공식 배송 목록에 안 나타납니다.

**실무 영향**: 핫월렛이 EOA(일반 주소)라면 대부분 안전합니다. 누락이 발생하는 유일한 경우는
"스마트 컨트랙트가 핫월렛에 ETH를 직접 보내는 경우"입니다 (예: 멀티시그 실행, DEX 정산, 에어드랍 컨트랙트).

**대응 전략**:
- Phase 1-2: 경고 문구 표시 + **잔고 대사로 간접 감지** (on-chain balance vs 장부 잔고 비교 → 차이 발생 시 알림)
- Phase 4: `debug_traceTransaction` 또는 `trace_block`으로 직접 수집 (아카이브 노드 필요)

### 4.6 토큰 추적 관리

지갑 등록 후, 해당 월렛에서 추적할 ERC-20 토큰을 등록한다.

#### 등록 플로우

1. 지갑 등록 → 추적 토큰 추가 (contract address + symbol + decimals)
2. ERC-20 Transfer 수집 시 등록된 토큰만 필터링하여 수집
3. 미등록 토큰의 Transfer 이벤트는 `raw_data`에 보존되지만 주요 화면에 표시하지 않음

#### UI 표시 규칙

| 요소 | 표시 방식 |
|------|----------|
| 토큰 식별 | contract address 대신 **symbol** 표시 (예: USDT, USDC) |
| symbol 클릭 | contract address를 **클립보드에 복사** |
| symbol 호버 | contract address를 **툴팁으로 표시** |
| 미등록 토큰 TX | TX 목록에서 contract address 표시 (symbol 미등록 상태) |

#### 스키마

`wallet_tracked_tokens` 테이블 (섹션 5.1 참조).

---

## 5. 데이터 모델

### 5.1 DB 스키마

```sql
-- 체인 메타데이터
CREATE TABLE chains (
    id VARCHAR(10) PRIMARY KEY,           -- 'ETH', 'POL', 'BSC', 'BTC', 'DOGE'
    name VARCHAR(100) NOT NULL,
    chain_type VARCHAR(10) NOT NULL,      -- 'EVM' or 'UTXO'
    native_symbol VARCHAR(10) NOT NULL,
    native_decimals INT NOT NULL,
    confirmation_blocks INT NOT NULL,
    block_time_seconds INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 감시 대상 월렛
CREATE TABLE wallets (
    id BIGSERIAL PRIMARY KEY,
    chain_id VARCHAR(10) NOT NULL REFERENCES chains(id),
    address VARCHAR(128) NOT NULL,         -- EVM: 42자, BTC bech32: 62자
    label VARCHAR(100),
    wallet_type VARCHAR(20) DEFAULT 'HOT', -- HOT, COLD
    sync_status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, ONBOARDING, IDLE, SYNCING, FAILED
    fail_count INT DEFAULT 0,
    cutoff_block BIGINT,                        -- 잔고 스냅샷 시점 블록 (NULL이면 미수집)
    cutoff_balance NUMERIC(38, 18),             -- cutoff_block 시점 네이티브 토큰 잔고
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (chain_id, address)
);

-- 월렛별 추적 토큰 등록 (Phase 1b)
CREATE TABLE wallet_tracked_tokens (
    id BIGSERIAL PRIMARY KEY,
    wallet_id BIGINT NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
    contract_address VARCHAR(128) NOT NULL,  -- ERC-20 contract address
    symbol VARCHAR(20) NOT NULL,             -- 예: USDT, USDC, WETH
    decimals INT NOT NULL DEFAULT 18,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (wallet_id, contract_address)
);

CREATE INDEX idx_tracked_tokens_wallet ON wallet_tracked_tokens(wallet_id);

-- 수집된 원본 트랜잭션
CREATE TABLE raw_transactions (
    id BIGSERIAL PRIMARY KEY,
    chain_id VARCHAR(10) NOT NULL REFERENCES chains(id),
    wallet_address VARCHAR(128) NOT NULL,
    tx_hash VARCHAR(128) NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp TIMESTAMP NOT NULL,
    tx_index INT,
    from_address VARCHAR(128),
    to_address VARCHAR(128),
    value_raw VARCHAR(78),                 -- uint256 원본 (정밀도 손실 방지)
    value_decimal NUMERIC(38, 18),         -- human-readable 변환값
    fee_raw VARCHAR(78),
    fee_decimal NUMERIC(38, 18),
    tx_status VARCHAR(10) NOT NULL,        -- CONFIRMED, FAILED
    raw_data JSONB NOT NULL,               -- 원본 RPC 응답 전체 보존
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (chain_id, tx_hash, wallet_address)
);

-- 동기화 커서
CREATE TABLE sync_cursors (
    id BIGSERIAL PRIMARY KEY,
    chain_id VARCHAR(10) NOT NULL REFERENCES chains(id),
    wallet_id BIGINT REFERENCES wallets(id),
    last_synced_block BIGINT NOT NULL DEFAULT 0,
    last_block_hash VARCHAR(128),          -- reorg 감지용
    status VARCHAR(20) DEFAULT 'IDLE',     -- IDLE, SYNCING, FAILED
    error_message TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (chain_id, wallet_id)
);

-- 인덱스
CREATE INDEX idx_raw_tx_chain_block ON raw_transactions(chain_id, block_number);
CREATE INDEX idx_raw_tx_chain_wallet ON raw_transactions(chain_id, wallet_address);
CREATE INDEX idx_raw_tx_hash ON raw_transactions(tx_hash);
CREATE INDEX idx_raw_tx_timestamp ON raw_transactions(block_timestamp);
```

### 5.2 통합 TX 모델 (NormalizedTransaction)

```
공통 필드: chain_id, tx_hash, block_number, block_timestamp, tx_index,
           from, to, value, fee, status, raw_data, wallet_address

EVM 전용 (raw_data JSONB 내부):
  gasUsed, gasPrice, effectiveGasPrice, nonce, inputData, logs[]

UTXO 전용 (raw_data JSONB 내부):
  vin[] (prevTxHash, prevVoutIndex, address, value)
  vout[] (voutIndex, address, value, scriptType)
  isCoinbase
```

### 5.3 대조 결과 (Phase 2)

```sql
-- Phase 2에서 추가
CREATE TABLE reconciliation_runs (
    id BIGSERIAL PRIMARY KEY,
    chain_id VARCHAR(10) NOT NULL,
    from_date TIMESTAMP NOT NULL,
    to_date TIMESTAMP NOT NULL,
    total_onchain INT NOT NULL,
    total_service_db INT NOT NULL,
    matched INT NOT NULL,
    onchain_only INT NOT NULL,
    db_only INT NOT NULL,
    amount_mismatch INT NOT NULL,
    failed_tx INT NOT NULL DEFAULT 0,
    executed_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE reconciliation_results (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT REFERENCES reconciliation_runs(id),
    chain_id VARCHAR(10) NOT NULL,
    tx_hash VARCHAR(128),
    match_status VARCHAR(20) NOT NULL,    -- MATCHED, ON_CHAIN_ONLY, DB_ONLY, AMOUNT_MISMATCH, FAILED_TX
    onchain_amount NUMERIC(38, 18),
    service_db_amount NUMERIC(38, 18),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

#### 5.3.1 reconciliation_runs 컬럼 상세

| 컬럼 | 의미 |
|------|------|
| `id` | 대조 실행 고유 식별자. 매 실행마다 1개 생성. |
| `chain_id` | 어떤 체인에 대해 실행했는지 (예: 'ETH', 'BTC'). |
| `from_date` / `to_date` | 대조 대상 기간. 재무팀이 "3월 1일~3월 31일" 선택 시 해당 범위. |
| `total_onchain` | 해당 기간 내 온체인에서 수집된 TX 총 건수. |
| `total_service_db` | 해당 기간 내 서비스 DB에 기록된 TX 총 건수. |
| `matched` | 양쪽 모두 존재하고 금액까지 일치하는 TX 건수. |
| `onchain_only` | 온체인에만 있고 서비스 DB에 없는 TX 건수 (미기록 의심). |
| `db_only` | 서비스 DB에만 있고 온체인에 없는 TX 건수 (허위 기록 의심). |
| `amount_mismatch` | 양쪽 모두 존재하지만 금액이 불일치하는 TX 건수. |
| `failed_tx` | 온체인에서 실패(reverted)한 TX 건수. gas fee만 소비됨. |
| `executed_at` | 대조 실행 시각. 감사 추적용. |

#### 5.3.2 reconciliation_results 컬럼 상세

| 컬럼 | 의미 |
|------|------|
| `id` | 개별 대조 결과 행의 고유 식별자. |
| `run_id` | 어떤 대조 실행(reconciliation_runs)에 속하는지 FK 참조. |
| `chain_id` | 해당 TX가 속한 체인. |
| `tx_hash` | 대조 대상 트랜잭션 해시. DB_ONLY인 경우 서비스 DB의 tx_hash, ON_CHAIN_ONLY인 경우 온체인 tx_hash. |
| `match_status` | 대조 결과 상태. 아래 5.3.3 참조. |
| `onchain_amount` | 온체인에서 확인된 금액 (wei/satoshi 변환 후 값). ON_CHAIN_ONLY일 때 서비스 DB 값은 NULL. |
| `service_db_amount` | 서비스 DB에 기록된 금액. DB_ONLY일 때 온체인 값은 NULL. |
| `notes` | 자동 생성 메모. 예: "금액 차이: 0.001 ETH", "수동 무시 처리됨" 등. |
| `created_at` | 결과 행 생성 시각. |

#### 5.3.3 match_status 값 상세

> **상수 관리**: `MatchStatus` enum으로 관리. 개발 중 필요 시 추가 가능 (예: `REPLACED`, `PENDING`).
> `domain/model/MatchStatus.kt`에 정의하여 단일 소스로 유지.

| 상태 | 의미 | 구체적 예시 |
|------|------|-----------|
| `MATCHED` | 온체인 TX와 서비스 DB 기록이 tx_hash로 매칭되고, 금액도 일치함 | 온체인에 tx `0xabc...`로 1.5 ETH 전송 기록이 있고, 서비스 DB에도 동일 tx_hash로 1.5 ETH 입금 처리가 되어 있음. **정상 상태.** |
| `ON_CHAIN_ONLY` | 온체인에 TX가 존재하지만 서비스 DB에 대응하는 기록이 없음 | 핫월렛 주소로 0.3 ETH가 전송되었으나, 거래소 시스템이 미감지. **미기록** 가능성. 원인: 시스템 장애, internal TX, 미등록 토큰, 직접 입금 등. |
| `AMOUNT_MISMATCH` | 양쪽 모두 tx_hash로 매칭되지만 금액이 다름 | 온체인 2.0 ETH 수신, 서비스 DB 1.98 ETH 기록. **금액 불일치.** 원인: gas fee 차감 오류, 소수점 처리 버그 등. |
| `FAILED_TX` | 온체인에서 TX가 실패(reverted)했지만 gas fee는 소비됨 | 출금 TX가 revert되어 전송 실패. **value=0이지만 gas fee만 빠져나감.** 서비스 DB에는 기록이 없음 (재시도로 성공한 TX로 override됨). 온체인에만 실패 TX가 남아 `ON_CHAIN_ONLY`로 잡히는데, 이때 tx_status=FAILED를 확인하여 `FAILED_TX`로 분류. gas fee 손실 추적용. |

#### 5.3.4 대조 실행 플로우

```
1. 재무팀이 대조 요청 (UI에서 체인, 기간 선택 후 "대조 실행" 클릭)
   │
2. reconciliation_runs 행 생성 (초기 카운트 0으로 INSERT)
   │
3. 온체인 TX 수집
   │  raw_transactions에서 해당 chain_id + 기간 내 TX 목록 조회
   │  → Set<tx_hash> onchainTxMap 생성
   │
4. 서비스 DB TX 수집
   │  ExchangeTransactionPort를 통해 해당 체인 + 기간 내 TX 목록 조회
   │  → Set<tx_hash> serviceDbTxMap 생성
   │
5. 1차 매칭: tx_hash 기준 정확 매칭
   │  ├── 양쪽 모두 존재 → 금액 비교
   │  │   ├── 금액 일치 → MATCHED
   │  │   └── 금액 불일치 → AMOUNT_MISMATCH
   │  ├── 온체인에만 존재 → ON_CHAIN_ONLY
   │  └── 서비스 DB에만 존재 → DB_ONLY
   │
5.5. FAILED_TX 재분류: ON_CHAIN_ONLY 중 tx_status 확인
   │  ON_CHAIN_ONLY 항목에서 raw_transactions.tx_status = 'FAILED'이면
   │  → FAILED_TX로 재분류 (gas fee만 소비된 실패 TX)
   │
6. (선택) 2차 Fuzzy 매칭: ON_CHAIN_ONLY와 DB_ONLY 중
   │  타임스탬프·금액이 유사한 쌍을 후보로 제시 (자동 매칭은 안 함)
   │
7. reconciliation_results 일괄 INSERT
   │  매 TX 결과를 한 행씩 기록
   │
8. reconciliation_runs 카운트 UPDATE
   │  matched, onchain_only, db_only, amount_mismatch, failed_tx 집계 반영
   │
9. 결과 반환 → UI에 대시보드 렌더링
```

#### 5.3.5 재무팀 활용 방법

- **일일 감사**: 전일 대조를 매일 아침 실행. 불일치 0건이면 정상. 1건이라도 있으면 상세 확인 후 원인 분류.
- **월말 결산**: 전체 월 기간으로 대조 실행. 결과를 CSV/Excel로 내보내어 감사 증빙 자료로 보관.
- **이상 거래 추적**: `ON_CHAIN_ONLY`는 미기록 입금 → 고객 지원팀에 전달하여 수동 입금 처리. `DB_ONLY`는 허위 기록 → 개발팀에 에스컬레이션하여 원인 분석.
- **트렌드 분석**: reconciliation_runs의 이력을 통해 불일치 발생 추이를 모니터링. 특정 체인에서 불일치가 증가하면 시스템 문제 가능성.

### 5.4 Phase 3 UTXO 체인 + 알림 데이터 모델

#### 5.4.1 UTXO TX의 raw_data JSONB 구조

UTXO 체인(BTC/DOGE)의 `raw_transactions.raw_data` JSONB는 아래 구조를 따른다.
EVM과 달리 from/to가 1:1이 아닌 N:M 구조이므로, vin/vout 배열로 전체 입출력을 보존한다.

```jsonc
{
  "txid": "a1b2c3d4e5f6...",
  "version": 2,
  "size": 225,
  "vsize": 166,            // SegWit 적용 시 가상 크기
  "weight": 661,
  "locktime": 0,
  "blockhash": "00000000000000000003abc...",
  "confirmations": 150,
  "blocktime": 1709280000,
  "isCoinbase": false,      // 마이닝 보상 TX 여부

  // 입력 (어디서 자금이 왔는가)
  "vin": [
    {
      "txid": "prev_tx_hash_1",       // 이전 UTXO의 TX 해시
      "vout": 0,                       // 이전 TX의 출력 인덱스
      "address": "bc1q...",            // 입력 주소 (디코딩 결과)
      "value": 0.50000000,            // 이전 UTXO 금액 (BTC 단위)
      "scriptSig": "...",
      "sequence": 4294967295
    },
    {
      "txid": "prev_tx_hash_2",
      "vout": 1,
      "address": "bc1p...",
      "value": 0.30000000
    }
  ],

  // 출력 (자금이 어디로 갔는가)
  "vout": [
    {
      "n": 0,                          // 출력 인덱스
      "value": 0.70000000,            // 수신 금액 (BTC 단위)
      "address": "bc1q_recipient...",  // 수신자 주소
      "scriptType": "witness_v0_keyhash",  // P2WPKH, P2PKH, P2SH 등
      "scriptPubKey": "..."
    },
    {
      "n": 1,
      "value": 0.09990000,            // 거스름돈 (change output)
      "address": "bc1q_sender_change...",
      "scriptType": "witness_v0_keyhash",
      "scriptPubKey": "..."
    }
  ],

  // 수수료는 명시적 필드가 없음, sum(vin.value) - sum(vout.value)로 계산
  "fee_calculated": 0.00010000
}
```

**핵심 포인트**:
- `vin[].value`와 `vout[].value`는 노드 버전에 따라 제공 여부가 달라질 수 있음. 미제공 시 이전 TX 조회로 보완 필요.
- `raw_transactions.from_address`에는 vin 중 감시 월렛 주소를, `to_address`에는 vout 중 감시 월렛 주소를 채운다 (N:M 중 관련 주소만 대표값).
- `raw_transactions.value_decimal`에는 감시 월렛 기준 순입출금액을 기록한다.

#### 5.4.2 알림 관련 테이블

```sql
-- Phase 3에서 추가
-- 알림 규칙 정의
CREATE TABLE alert_rules (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,            -- 규칙 이름 (예: "ETH 미기록 입금 감지")
    rule_type VARCHAR(30) NOT NULL,        -- ONCHAIN_ONLY, DB_ONLY, AMOUNT_MISMATCH,
                                           -- SYNC_FAILURE, BALANCE_DIFF
    chain_id VARCHAR(10),                  -- NULL이면 전체 체인 대상
    threshold_count INT DEFAULT 1,         -- 알림 발생 기준 건수 (N건 이상일 때 알림)
    threshold_amount NUMERIC(38, 18),      -- 알림 발생 기준 금액 (특정 금액 이상일 때 알림)
    notify_channel VARCHAR(20) NOT NULL,   -- IN_APP, SLACK, BOTH
    slack_webhook_url TEXT,                -- Slack 알림 시 webhook URL
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 발생한 알림 이력
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    alert_rule_id BIGINT REFERENCES alert_rules(id),
    chain_id VARCHAR(10) NOT NULL,
    severity VARCHAR(10) NOT NULL,         -- INFO, WARN, CRITICAL
    title VARCHAR(200) NOT NULL,           -- 알림 제목 (예: "[ETH] 미기록 입금 3건 감지")
    message TEXT NOT NULL,                 -- 상세 내용
    related_run_id BIGINT,                 -- 관련 대조 실행 ID (있을 경우)
    related_tx_hash VARCHAR(128),          -- 관련 TX (있을 경우)
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    is_resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_by VARCHAR(100),              -- 처리자 이름 (수동 입력)
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_unread ON notifications(is_read) WHERE is_read = FALSE;
CREATE INDEX idx_notifications_chain ON notifications(chain_id, created_at DESC);
```

#### 5.4.3 수동 매칭/무시 처리 테이블

대조 결과 중 `ON_CHAIN_ONLY`, `DB_ONLY` 항목에 대해 재무팀이 수동으로 사유를 기록하고 처리 상태를 관리하는 테이블.

```sql
-- Phase 3에서 추가
-- 수동 매칭: 자동 매칭 실패 건을 재무팀이 수동으로 연결
CREATE TABLE manual_matches (
    id BIGSERIAL PRIMARY KEY,
    reconciliation_result_id BIGINT NOT NULL REFERENCES reconciliation_results(id),
    matched_with_result_id BIGINT REFERENCES reconciliation_results(id),  -- 대응하는 상대 건
    matched_with_tx_hash VARCHAR(128),     -- 또는 직접 tx_hash 지정
    match_reason TEXT NOT NULL,            -- 매칭 사유 (예: "gas fee 차감 후 일치 확인")
    matched_by VARCHAR(100) NOT NULL,      -- 처리자 이름
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 무시 처리: 대조 불일치이지만 문제없는 건을 무시 처리
CREATE TABLE ignored_discrepancies (
    id BIGSERIAL PRIMARY KEY,
    reconciliation_result_id BIGINT NOT NULL REFERENCES reconciliation_results(id),
    ignore_reason VARCHAR(30) NOT NULL,    -- DUST_AMOUNT (소액 무시), KNOWN_ISSUE (알려진 이슈),
                                           -- TEST_TX (테스트 TX), MANUAL_VERIFIED (수동 확인 완료),
                                           -- OTHER
    notes TEXT,                            -- 상세 사유
    ignored_by VARCHAR(100) NOT NULL,      -- 처리자 이름
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (reconciliation_result_id)      -- 하나의 불일치에 하나의 무시 처리만
);

CREATE INDEX idx_manual_matches_result ON manual_matches(reconciliation_result_id);
CREATE INDEX idx_ignored_result ON ignored_discrepancies(reconciliation_result_id);
```

**운영 플로우**:
1. 대조 실행 → 불일치 항목 발견
2. 재무팀이 각 불일치 항목을 확인
3. 수동 매칭 가능한 건: `manual_matches`에 등록 (예: Fuzzy 매칭 후보 중 실제 매칭 확인)
4. 무시 처리할 건: `ignored_discrepancies`에 등록 (예: 0.000001 ETH 소액 dust 입금)
5. 다음 대조 실행 시, 이미 수동 매칭/무시된 건은 카운트에서 제외 가능

### 5.5 Phase 4 대비 예약 컬럼

`raw_transactions`의 `raw_data` JSONB에 gas 정보가 이미 포함되므로 별도 컬럼 추가 불필요.
Phase 4에서 `gas_summary` 뷰 또는 materialized view로 추출.

---

## 6. Phase별 기능 명세

### Phase 1a: 클린 리셋 + 멀티체인 기반 스키마 (3~4일)

| # | 기능 | 수락 기준 |
|---|------|----------|
| 1a-1 | 기존 코드를 `archived/` 폴더로 이동 | 빌드 대상에서 제외됨 |
| 1a-2 | 패키지 구조 재설정 (`com.example.txcollector`) | `task verify` 통과 |
| 1a-3 | `chains` 테이블 + 시드 (ETH, POL, BSC, BTC, DOGE) | Flyway V1 마이그레이션 |
| 1a-4 | `wallets` 테이블 (chain_id FK 포함) | 지갑 CRUD API 동작 |
| 1a-5 | `raw_transactions` 테이블 (chain_id 포함) | UNIQUE(chain_id, tx_hash, wallet_address) |
| 1a-6 | `sync_cursors` 테이블 | 체인별 커서 관리 가능 |
| 1a-7 | 지갑 등록/삭제/목록 REST API | 체인 미지정 시 400 반환 |
| 1a-8 | Docker Compose (app + postgres) | `docker compose up`으로 실행 |

**완료 기준**: 모든 테이블 생성됨, 지갑 CRUD 동작, `task verify` 통과

### Phase 1b: EVM 멀티체인 TX 수집 (7~10일)

| # | 기능 | 수락 기준 |
|---|------|----------|
| 1b-1 | `EvmRpcClient` (체인별 RPC URL 주입) | ETH/POL/BSC 3개 체인 연결 확인 |
| 1b-2 | `EvmChainAdapter` (fetchTransactions 구현) | Native ETH + ERC-20 Transfer 수집 |
| 1b-3 | `ChainAdapterRegistry` (체인 라우팅) | 체인별 어댑터 자동 등록 |
| 1b-4 | 배치 스케줄러 (@Scheduled, 5분 주기) | 체인별 병렬 수집 동작 |
| 1b-5 | 증분 동기화 (sync_cursors 기반) | 재시작 후 이어서 수집 |
| 1b-6 | Reorg 안전 마진 | 체인별 confirmation blocks 적용 |
| 1b-7 | 멱등성 보장 | 중복 스캔해도 데이터 중복 없음 (ON CONFLICT DO NOTHING) |
| 1b-8 | 동시 실행 방지 | 같은 월렛 중복 동기화 차단 |
| 1b-9 | TX 목록 조회 API (필터: 체인, 월렛, 날짜, tx hash) | 모든 필터 동작 |
| 1b-10 | TX 검색 API (tx hash) | tx hash로 체인 무관 검색 |
| 1b-11 | 프론트엔드: 지갑 관리 페이지 | 체인 선택 + 등록/삭제/상태 + 토큰 추적 관리 |
| 1b-12 | 프론트엔드: TX 목록 페이지 | 필터 + 페이지네이션 + 블록 탐색기 링크 |
| 1b-13 | 프론트엔드: 대시보드 (동기화 신호등) | 체인별 초록/노랑/빨강 |
| 1b-14 | RetryExecutor + RateLimiter | RPC 실패 시 재시도, rate limit 준수 |
| 1b-15 | 통합 테스트 (Testcontainers + MockWebServer) | 핵심 수집 로직 커버 |
| 1b-16 | `wallet_tracked_tokens` 테이블 + 토큰 등록 API | 월렛별 ERC-20 토큰 추적 등록/삭제 |

**완료 기준**: ETH/POL/BSC 실제 메인넷 지갑 1개씩 수집 후 Etherscan/Polygonscan/BscScan과 비교해 누락 없음

### Phase 2: 서비스 DB 대조 (2~3주)

| # | 기능 | 수락 기준 |
|---|------|----------|
| 2-1 | `ExchangeTransactionPort` 인터페이스 | tx 조회 메서드 정의 |
| 2-2 | `DatabricksExchangeAdapter` 구현 (REST API/JDBC, read-only, Phase 2 기본) | fact_crypto_transactions 연동. ADR-004 참조. |
| 2-3 | 로컬 테스트용 exchange DB 스키마 (docker-compose) | 더미 데이터 포함 |
| 2-4 | 대조 엔진 (tx_hash 1차 매칭 + fuzzy 2차) | MATCHED/ON_CHAIN_ONLY/DB_ONLY/AMOUNT_MISMATCH/FAILED_TX 분류 |
| 2-5 | `reconciliation_runs` + `reconciliation_results` 테이블 | 대조 실행 이력 관리 |
| 2-6 | 잔고 대사 (on-chain balance vs DB 잔고 비교) | 차이 발생 시 경고 |
| 2-7 | 대조 결과 조회 API | 필터: 체인, 상태, 날짜 |
| 2-8 | 프론트엔드: 대조 대시보드 | 매칭률, 불일치 건수, 상세 목록 |
| 2-9 | CSV 내보내기 (대조 결과) | 감사 증빙용 |
| 2-10 | Excel(XLSX) 내보내기 (대조 결과 + TX 목록) | 재무팀 요청. 아래 상세 참조. |

#### Excel 내보내기 상세 (2-10)

**배경**: 재무팀이 감사 증빙 및 월말 결산 보고용으로 Excel 내보내기를 요청.
CSV는 한글 깨짐, 셀 서식 없음, 시트 분리 불가 등의 한계가 있어 실무에서 불편.

**지원 범위** (유용도 순):

| # | 내보내기 대상 | 포함 내용 | 활용 목적 |
|---|-------------|----------|----------|
| 1 | **대조 결과 리포트** | 요약 시트(매칭률, 체인별 건수) + 상세 시트(불일치 항목 전체) | 월말 결산 감사 증빙 |
| 2 | **불일치 항목 목록** | ON_CHAIN_ONLY / DB_ONLY / AMOUNT_MISMATCH / FAILED_TX 상세 + 수동 처리 상태 | 이상 거래 조치 추적 |
| 3 | **TX 목록** (기간/체인/월렛 필터) | tx_hash, 금액, 시각, from/to 등 | 고객 문의 대응, 기간별 거래 내역 확인 |

**구현 방식**:
- 라이브러리: Apache POI (기술 스택에 재추가)
- 엔드포인트: `GET /api/export/reconciliation/{runId}.xlsx`, `GET /api/export/transactions.xlsx?chainId=&from=&to=`
- 시트 서식: 헤더 색상, 금액 숫자 포맷, 날짜 포맷, 자동 열 너비 적용
- 대용량 대응: SXSSFWorkbook (스트리밍) 사용, 10만 행 초과 시 분할 시트

**완료 기준**: 서비스 DB와 대조하여 누락/불일치 항목을 정확히 식별하고 리포트 생성 (CSV + Excel)

### Phase 3: UTXO 체인 + UI 고도화 (3~4주)

| # | 기능 | 수락 기준 |
|---|------|----------|
| 3-1 | `UtxoRpcClient` (BTC/DOGE) | 블록 스캔 + 주소 필터 |
| 3-2 | `BitcoinAdapter`, `DogeAdapter` | ChainAdapter 인터페이스 구현 |
| 3-3 | UTXO TX → raw_transactions 저장 | vin/vout이 raw_data JSONB에 보존 |
| 3-4 | UTXO 체인 서비스 DB 대조 | Phase 2 대조 엔진 확장 |
| 3-5 | 알림 시스템 (인앱 + Slack webhook) | 누락 발견 시 알림 |
| 3-6 | UI 고도화: 대시보드 통계, 차트, 리포트 | 기간별 TX 추이 |

**완료 기준**: 5개 체인 전체 수집/대조 동작, 통합 대시보드에서 확인 가능

### Phase 4: Gas Fee / 잔고 역산 / Internal TX (2~3주)

| # | 기능 | 수락 기준 |
|---|------|----------|
| 4-1 | Gas fee 추출/집계 (raw_data에서) | 월렛별, 기간별 gas 합계 |
| 4-2 | 특정 시점 잔고 역산 (eth_getBalance at block) | 날짜 입력 → 잔고 표시 |
| 4-3 | Internal TX 수집 (trace API) | 누락 TX 추가 식별 |
| 4-4 | UTXO 잔고 역산 (listunspent) | BTC/DOGE 잔고 |

---

## 7. 체인별 기술 사양

### 7.1 EVM 체인 (ETH, POL, BSC)

| 항목 | ETH | POL | BSC |
|------|-----|-----|-----|
| 블록 시간 | ~12초 | ~2초 | ~3초 |
| 5분 배치 시 블록 수 | ~25 | ~150 | ~100 |
| Confirmation blocks | 12 | 128 | 15 |
| getLogs 범위 제한 | ~10,000 | ~3,500 | ~5,000 |
| Native symbol | ETH | POL | BNB |
| Decimals | 18 | 18 | 18 |

**수집 방식**: 2-track 병렬
- Track 1: `eth_getBlockByNumber(full=true)` → native transfer 필터
- Track 2: `eth_getLogs(Transfer topic)` → ERC-20 transfer 필터
- 수집된 tx hash 합집합에 대해 `eth_getTransactionReceipt` 호출

### 7.2 UTXO 체인 (BTC, DOGE)

| 항목 | BTC | DOGE |
|------|-----|------|
| 블록 시간 | ~10분 | ~1분 |
| Confirmation blocks | 6 | 40 |
| 주소 형식 | P2PKH(34자), bech32(42~62자) | P2PKH(34자, D prefix) |
| 수집 방식 | getblock(hash, 2) | 동일 |
| 잔고 조회 | listunspent | 동일 |

**미결 사항**: BTC/DOGE public API (Blockstream, Blockcypher) vs 자체 노드 RPC.
자체 노드가 있으면 `listsinceblock`이 효율적. public API만 있으면 REST 기반 어댑터.

### 7.3 Reorg 안전 마진

모든 체인에서 `safeBlock = latestBlock - confirmationBlocks`까지만 수집.
확정되지 않은 블록의 데이터는 저장하지 않음.

---

## 8. 이식성 및 배포

### 8.1 실행 의존성

`Docker + .env 파일`만으로 실행 가능. JDK/Node 로컬 설치 불필요.

```bash
git clone <repo>
cp .env.example .env
# .env 편집 (RPC URL 등)
docker compose up -d
```

### 8.2 환경변수 설계

```bash
# Database
DB_URL=jdbc:postgresql://localhost:5432/txcollector
DB_USERNAME=txcollector
DB_PASSWORD=txcollector

# Scheduler
SCHEDULER_ENABLED=true
BATCH_INTERVAL_MS=300000  # 5분

# Chain: Ethereum
CHAIN_ETH_ENABLED=true
ETH_RPC_URL=https://eth.drpc.org           # 개인PC: public
# ETH_RPC_URL=http://eth-node.internal:8545  # 회사PC: 자체 노드

# Chain: Polygon
CHAIN_POL_ENABLED=true
POL_RPC_URL=https://polygon-rpc.com

# Chain: BSC
CHAIN_BSC_ENABLED=true
BSC_RPC_URL=https://bsc-dataseed.binance.org

# Chain: Bitcoin (Phase 3)
CHAIN_BTC_ENABLED=false
BTC_RPC_URL=

# Chain: Dogecoin (Phase 3)
CHAIN_DOGE_ENABLED=false
DOGE_RPC_URL=

# Exchange DB (Phase 2)
EXCHANGE_DB_ENABLED=false
EXCHANGE_DB_ADAPTER=databricks                             # databricks (기본) | athena | mysql
EXCHANGE_DB_URL=                                           # 어댑터별 URL 형식 상이
EXCHANGE_DB_USERNAME=
EXCHANGE_DB_PASSWORD=
```

### 8.3 개인PC → 회사PC 체크리스트

1. `.env`에서 RPC URL을 자체 노드 주소로 변경
2. `EXCHANGE_DB_*`를 거래소 DB read replica로 변경
3. `docker compose up -d`
4. 끝

---

## 9. 기술 스택

| 영역 | 기술 |
|------|------|
| Backend | Kotlin 2.x, Spring Boot 3.4, JPA, Flyway |
| Frontend | Next.js 15, React 18, Tailwind CSS |
| Database | PostgreSQL 16 |
| RPC Client | Spring WebFlux WebClient (비동기) |
| Infra | Docker Compose |
| Test | JUnit 5, Testcontainers, MockWebServer |
| Task Runner | Taskfile |

**제거된 의존성** (기존 프로젝트 대비):
- web3j (직접 RPC 호출로 대체)
- Spring Security (불필요)
- CoinGecko Client (가격 조회 불필요)
- OpenCSV (Phase 2에서 필요 시 추가)

---

## 10. 리스크 및 미결 사항

| # | 리스크 | 영향 | 대응 |
|---|--------|------|------|
| R1 | Internal TX 누락 (Phase 1-2) | ETH 입금 일부 미추적 | 잔고 대사로 간접 감지 + Phase 4에서 해소 |
| R2 | Public node rate limit | 대량 히스토리 수집 시 수시간 소요 | adaptive range + RateLimiter + 자체 노드 전환 |
| R3 | UTXO 모델 복잡도 | BTC/DOGE 구현에 예상보다 시간 소요 | Phase 3으로 분리, EVM 우선 |
| R4 | 서비스 DB 스키마 변경 | 대조 쿼리 수정 필요 | SQL 외부화 (application.yml) |
| R5 | 소수점 정밀도 | 비교 시 반올림 차이로 오탐 | 모든 금액을 wei/satoshi 원본으로 비교 |
| R6 | 비표준 ERC-20 | Transfer 이벤트 미발생 토큰 | 알려진 토큰 화이트리스트 관리 |

---

## 11. 용어 정의

| 용어 | 설명 |
|------|------|
| 핫월렛 (Hot Wallet) | 온라인에 연결된 거래소 운영 월렛. 입출금 처리용. |
| 콜드월렛 (Cold Wallet) | 오프라인 보관 월렛. 대량 자산 보관용. |
| EOA | Externally Owned Account. 개인키로 직접 서명하는 일반 주소. |
| Internal TX | 스마트 컨트랙트 실행 중 발생하는 ETH 전송. 블록 tx 목록에 미포함. |
| Finality | 트랜잭션이 되돌릴 수 없게 확정되기까지 필요한 확인 블록 수. |
| UTXO | Unspent Transaction Output. BTC/DOGE의 잔고 모델. |
| Reorg | 블록체인 재조직. 확정 전 블록이 다른 블록으로 교체되는 현상. |
| Reconciliation | 두 데이터 소스(온체인 vs 서비스 DB) 간 대조/검증. |

---

## 12. 프로젝트 구조

```
txcollector/
├── backend/
│   └── src/main/kotlin/com/example/txcollector/
│       ├── adapter/
│       │   ├── inbound/        # REST Controller, Scheduler (외부 → 앱)
│       │   └── outbound/       # JPA Repository, RPC Client, Exchange DB (앱 → 외부)
│       ├── application/
│       │   ├── port/
│       │   │   ├── input/      # UseCase 인터페이스 (인바운드 포트)
│       │   │   └── output/     # Repository, ChainAdapter 인터페이스 (아웃바운드 포트)
│       │   ├── service/        # UseCase 구현체
│       │   └── dto/            # Request/Response DTOs
│       ├── domain/
│       │   ├── service/        # 도메인 비즈니스 로직
│       │   └── model/          # Chain, Wallet, RawTransaction, SyncCursor
│       └── config/             # SchedulerConfig, DataSourceConfig
├── frontend/
│   └── src/
│       ├── app/                # Pages (App Router)
│       ├── components/         # UI Components
│       └── lib/                # API client, utils
├── docker-compose.yml
├── .env.example
├── Taskfile.yml
├── PRD.md                      # 이 문서
├── CLAUDE.md                   # AI 개발 가이드라인
└── archived/                   # 기존 코드 보관
```
