---
title: "Blockchain Double-Entry Bookkeeping"
version: 1
status: approved
feature: "blockchain-double-entry-bookkeeping"
date: "2026-02-22"
---

## Overview

Ethereum 블록체인 데이터를 기반으로 특정 지갑 주소의 복식부기 원장(Journal Entries)을 자동 생성하는 **감사 워크벤치** 웹 애플리케이션. 회계사/세무사가 초안 분개를 검토, 수정, 승인한 뒤 CSV/Excel로 내보낼 수 있다.

## User Scenarios

### Scenario 1: 지갑 트랜잭션 → 복식부기 원장 생성
**As a** 회계사,
**I want to** 클라이언트의 Ethereum 지갑 주소를 입력하여 해당 지갑의 모든 거래를 복식부기 분개로 자동 변환받고 싶다,
**So that** 수작업 없이 감사 가능한 초안 원장을 빠르게 확보할 수 있다.

**Steps**:
1. 웹 앱에 접속하여 Ethereum 지갑 주소(0x...)를 입력
2. 시스템이 Ethereum JSON-RPC를 통해 해당 지갑의 전체 트랜잭션 이력을 수집
3. 각 트랜잭션을 분류(ETH 전송, ERC-20 전송, 가스비, Uniswap 스왑 등)하고 복식부기 분개를 자동 생성
4. 분개 목록이 테이블 형태로 표시됨 (날짜, 설명, 차변/대변, KRW 금액, 상태)
5. 미분류 거래는 "검토 필요" 상태로 표시

### Scenario 2: 분개 검토 및 수정
**As a** 회계사,
**I want to** 자동 생성된 분개를 검토하고 잘못된 분류를 수정하고 싶다,
**So that** 감사 보고서에 사용할 수 있는 정확한 원장을 확보할 수 있다.

**Steps**:
1. 분개 목록에서 특정 분개를 클릭하여 상세 보기
2. 원본 트랜잭션 해시, 적용된 분류 규칙, 사용된 가격 출처를 확인
3. 계정과목, 금액, 메모를 수정
4. 수정 이력이 자동으로 기록됨 (who/when/what)
5. "승인" 버튼으로 최종 확정

### Scenario 3: 원장 내보내기
**As a** 회계사,
**I want to** 승인된 분개 데이터를 CSV/Excel로 내보내고 싶다,
**So that** 기존 회계 소프트웨어(더존, SAP 등)에 임포트하거나 감사 증빙으로 사용할 수 있다.

**Steps**:
1. 기간 필터를 설정 (2025-01-01 ~ 2025-12-31)
2. "내보내기" 버튼 클릭
3. CSV 또는 Excel 형식 선택
4. 분개 데이터 + 감사 추적 정보가 포함된 파일 다운로드

### Scenario 4: 미분류 거래 수동 처리
**As a** 회계사,
**I want to** 시스템이 자동 분류하지 못한 거래를 직접 분개로 변환하고 싶다,
**So that** 누락 없이 모든 거래가 원장에 반영된다.

**Steps**:
1. "검토 필요" 필터로 미분류 거래 목록 확인
2. 거래의 원본 데이터(from, to, value, logs) 확인
3. 수동으로 계정과목 지정 및 차변/대변 금액 입력
4. 해당 거래를 "수동 분류"로 표시하고 저장

## Core Requirements

### Functional

#### 데이터 수집
- [ ] FR-1: 시스템은 유효한 Ethereum 주소(0x, 42자)를 입력받아 해당 주소의 전체 트랜잭션 이력을 수집해야 한다 (MUST)
- [ ] FR-2: 표준 Ethereum JSON-RPC (`eth_getLogs`, `eth_getBlockByNumber`, `eth_getTransactionReceipt`, `eth_getTransactionByHash`, `eth_blockNumber`)를 사용하여 ETH, ERC-20, ERC-721, ERC-1155 전송 이력을 수집해야 한다 (MUST)
- [ ] FR-3: 수집된 원본 데이터(RPC 응답 JSON)는 Layer 1으로 불변 저장해야 한다 (MUST)
- [ ] FR-4: 페이지네이션을 통해 대량 트랜잭션(10,000건 이상)도 처리해야 한다 (MUST)
- [ ] FR-5: 실패한 트랜잭션(status=0x0)도 수집하여 가스비 분개를 생성해야 한다 (MUST)

#### 트랜잭션 분류
- [ ] FR-6: 다음 트랜잭션 유형을 자동 분류해야 한다 (MUST):
  - ETH 전송 (incoming/outgoing)
  - ERC-20 토큰 전송 (incoming/outgoing)
  - 가스비 (모든 outgoing 트랜잭션)
  - Uniswap V2 스왑 (`Swap` 이벤트)
  - Uniswap V3 스왑 (`Swap` 이벤트)
- [ ] FR-7: 분류 불가능한 트랜잭션은 `UNCLASSIFIED` 상태로 표시하고 수동 검토 대기열에 추가해야 한다 (MUST)
- [ ] FR-8: 각 분류 결과에 적용된 분류 규칙 ID를 기록해야 한다 (MUST)
- [ ] FR-9: 분류 엔진은 플러그인/어댑터 구조로 설계하여 새로운 프로토콜을 추가할 수 있어야 한다 (MUST)

#### 복식부기 분개 생성
- [ ] FR-10: 분류된 회계 이벤트를 복식부기 분개(Journal Entry)로 변환해야 한다. 모든 분개의 차변 합계 = 대변 합계 (MUST)
- [ ] FR-11: FIFO(선입선출법) 원가 기준으로 자산 처분 시 실현 손익을 계산해야 한다 (MUST)
- [ ] FR-12: 모든 금액은 KRW로 환산하여 기록해야 한다. 환산 시점은 트랜잭션의 블록 타임스탬프 기준 (MUST)
- [ ] FR-13: 기본 계정과목(Chart of Accounts)을 제공해야 한다 (MUST):
  - `자산:암호화폐:ETH` — ETH 보유
  - `자산:암호화폐:ERC20:{SYMBOL}` — 토큰별 계정
  - `자산:외부` — 외부 상대 계정
  - `비용:가스비` — 네트워크 수수료
  - `비용:거래수수료` — DEX 수수료
  - `수익:실현이익` — 자산 처분 이익
  - `비용:실현손실` — 자산 처분 손실
  - `수익:미지정수입` — 출처 미지정 수입
  - `수익:에어드롭` — 에어드롭 수령 (수동 분류 시)
- [ ] FR-14: 사용자가 계정과목을 추가/수정/삭제할 수 있어야 한다 (MUST)

#### 감사 추적 (Audit Trail)
- [ ] FR-15: 모든 분개는 원본 트랜잭션 해시에 연결되어야 한다 (MUST)
- [ ] FR-16: 모든 분개는 적용된 분류 규칙과 사용된 가격 출처(CoinGecko 등)를 기록해야 한다 (MUST)
- [ ] FR-17: 분개 수정 시 수정 전/후 값, 수정 시각을 기록해야 한다 (MUST)
- [ ] FR-18: 감사 추적 데이터는 삭제할 수 없어야 한다 (MUST NOT delete)

#### 검토/수정 UI
- [ ] FR-19: 분개 목록을 테이블 형태로 표시해야 한다 (날짜, 설명, 차변계정, 대변계정, 금액, 상태) (MUST)
- [ ] FR-20: 분개를 필터링할 수 있어야 한다: 기간, 상태(자동분류/수동분류/검토필요/승인됨), 계정과목 (MUST)
- [ ] FR-21: 개별 분개의 상세 보기에서 원본 tx 데이터, 분류 규칙, 가격 출처를 확인할 수 있어야 한다 (MUST)
- [ ] FR-22: 분개의 계정과목, 금액, 메모를 수정할 수 있어야 한다 (MUST)
- [ ] FR-23: 분개를 "승인" 상태로 변경할 수 있어야 한다 (MUST)

#### 내보내기
- [ ] FR-24: 분개 데이터를 CSV 형식으로 내보낼 수 있어야 한다 (MUST)
- [ ] FR-25: 분개 데이터를 Excel(.xlsx) 형식으로 내보낼 수 있어야 한다 (MUST)
- [ ] FR-26: 내보내기 시 기간 필터를 적용할 수 있어야 한다 (MUST)

#### 가격 데이터
- [ ] FR-27: CoinGecko API를 통해 토큰의 히스토리컬 가격(KRW)을 조회해야 한다 (MUST)
- [ ] FR-28: 조회된 가격 데이터는 캐싱하여 동일 토큰/날짜 재조회를 방지해야 한다 (MUST)
- [ ] FR-29: CoinGecko에서 가격을 찾을 수 없는 토큰은 가격을 0으로 표시하고 "가격 미확인" 플래그를 설정해야 한다 (MUST)

### Non-Functional

- [ ] NFR-1: 1,000건 트랜잭션의 수집 및 분개 생성은 5분 이내에 완료되어야 한다 (MUST)
- [ ] NFR-2: 분개 목록 페이지 로딩은 2초 이내여야 한다 (MUST)
- [ ] NFR-3: 모든 금액 계산은 소수점 18자리(wei 정밀도)까지 처리해야 한다 (MUST)
- [ ] NFR-4: PostgreSQL 데이터베이스의 모든 금융 데이터 테이블에 트랜잭션 격리 수준 SERIALIZABLE을 사용해야 한다 (MUST)
- [ ] NFR-5: 시스템은 Docker Compose로 로컬 개발 환경을 구성할 수 있어야 한다 (MUST)

## Technical Details

### Architecture

```
┌─────────────────────────────────────────────────────┐
│                   Next.js Frontend                  │
│          (React + TypeScript, Tailwind CSS)          │
│   ┌─────────┐  ┌──────────┐  ┌──────────────────┐  │
│   │ Wallet  │  │ Journal  │  │    Export         │  │
│   │ Input   │  │ Review   │  │    (CSV/Excel)    │  │
│   └────┬────┘  └────┬─────┘  └────────┬─────────┘  │
└────────┼────────────┼─────────────────┼─────────────┘
         │            │                 │
         ▼            ▼                 ▼
┌─────────────────────────────────────────────────────┐
│              Kotlin + Spring Boot Backend           │
│                                                     │
│  ┌──────────────────────────────────────────────┐   │
│  │              REST API Layer                   │   │
│  │   /api/wallets  /api/journals  /api/export   │   │
│  └──────────────────┬───────────────────────────┘   │
│                     │                               │
│  ┌──────────────────▼───────────────────────────┐   │
│  │              Domain Layer                     │   │
│  │                                               │   │
│  │  ┌─────────────┐  ┌────────────────────────┐ │   │
│  │  │ Ingestion   │  │ Classification Engine  │ │   │
│  │  │ Service     │  │ (Plugin Architecture)  │ │   │
│  │  └──────┬──────┘  └──────────┬─────────────┘ │   │
│  │         │                    │                │   │
│  │  ┌──────▼──────┐  ┌─────────▼──────────────┐ │   │
│  │  │ Price       │  │ Ledger Engine          │ │   │
│  │  │ Service     │  │ (Double-Entry + FIFO)  │ │   │
│  │  └─────────────┘  └────────────────────────┘ │   │
│  └──────────────────────────────────────────────┘   │
│                     │                               │
│  ┌──────────────────▼───────────────────────────┐   │
│  │           Infrastructure Layer                │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────────┐  │   │
│  │  │ Ethereum │ │CoinGecko │ │ PostgreSQL   │  │   │
│  │  │RPC Client│ │ Client   │ │ Repository   │  │   │
│  │  └──────────┘ └──────────┘ └──────────────┘  │   │
│  └──────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
         │              │              │
         ▼              ▼              ▼
   ┌──────────┐  ┌──────────┐  ┌──────────────┐
   │ Ethereum │  │CoinGecko │  │ PostgreSQL   │
   │ JSON-RPC │  │  API     │  │   Database   │
   └──────────┘  └──────────┘  └──────────────┘
```

**3-Layer Data Architecture**:
- **Layer 1 (Raw)**: `raw_transactions` 테이블 — RPC 응답 JSON 원본 저장. 불변.
- **Layer 2 (Normalized)**: `accounting_events` 테이블 — 분류된 회계 이벤트. 재분류 가능.
- **Layer 3 (Ledger)**: `journal_entries` + `journal_lines` 테이블 — 복식부기 분개. 수정 시 이력 보존.

**분류 엔진 플러그인 구조**:
```
ClassifierPlugin (interface)
├── EthTransferClassifier      — ETH 전송 (incoming/outgoing)
├── Erc20TransferClassifier    — ERC-20 토큰 전송
├── GasFeeClassifier           — 가스비 (모든 outgoing tx)
├── UniswapV2Classifier        — Uniswap V2 Swap 이벤트
├── UniswapV3Classifier        — Uniswap V3 Swap 이벤트
└── UnclassifiedFallback       — 매칭되지 않는 모든 거래
```

### API Changes

신규 REST API (Backend → Frontend):

```
# 지갑 관리
POST   /api/wallets                    — 지갑 주소 등록 및 트랜잭션 수집 시작
GET    /api/wallets                    — 등록된 지갑 목록
GET    /api/wallets/{address}/status   — 수집/처리 진행 상태

# 분개 (Journal Entries)
GET    /api/journals                   — 분개 목록 (필터: 기간, 상태, 계정과목)
GET    /api/journals/{id}              — 분개 상세 (원본 tx, 분류 규칙, 가격 출처 포함)
PATCH  /api/journals/{id}              — 분개 수정 (계정과목, 금액, 메모)
POST   /api/journals/{id}/approve      — 분개 승인
POST   /api/journals/bulk-approve      — 다건 일괄 승인

# 미분류 거래
GET    /api/unclassified               — 미분류 거래 목록
POST   /api/unclassified/{id}/classify — 수동 분류 (계정과목 지정)

# 계정과목
GET    /api/accounts                   — 계정과목 목록
POST   /api/accounts                   — 계정과목 추가
PATCH  /api/accounts/{id}              — 계정과목 수정
DELETE /api/accounts/{id}              — 계정과목 삭제

# 내보내기
POST   /api/export                     — 분개 내보내기 (CSV/Excel, 기간 필터)

# 가격
GET    /api/prices/{token}/{date}      — 특정 토큰의 특정 날짜 KRW 가격
```

### Data Model

```sql
-- Layer 1: 원본 체인 데이터 (불변)
CREATE TABLE raw_transactions (
    id              BIGSERIAL PRIMARY KEY,
    wallet_address  VARCHAR(42) NOT NULL,
    tx_hash         VARCHAR(66) NOT NULL UNIQUE,
    block_number    BIGINT NOT NULL,
    block_timestamp TIMESTAMP NOT NULL,
    raw_data        JSONB NOT NULL,          -- RPC 응답 원본
    tx_status       SMALLINT NOT NULL,       -- 0: failed, 1: success
    created_at      TIMESTAMP DEFAULT NOW()
);

-- Layer 2: 분류된 회계 이벤트
CREATE TABLE accounting_events (
    id                  BIGSERIAL PRIMARY KEY,
    raw_transaction_id  BIGINT REFERENCES raw_transactions(id),
    event_type          VARCHAR(50) NOT NULL,  -- INCOMING, OUTGOING, SWAP, FEE, UNCLASSIFIED
    classifier_id       VARCHAR(100) NOT NULL, -- 적용된 분류 규칙 ID
    token_address       VARCHAR(42),           -- null = ETH
    token_symbol        VARCHAR(20),
    amount_raw          NUMERIC(78, 0) NOT NULL, -- wei 단위 원본
    amount_decimal      NUMERIC(38, 18) NOT NULL, -- 토큰 단위
    counterparty        VARCHAR(42),           -- 상대방 주소
    price_krw           NUMERIC(24, 8),        -- 이벤트 시점 KRW 단가
    price_source        VARCHAR(50),           -- COINGECKO, MANUAL, UNKNOWN
    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW()
);

-- Layer 3: 복식부기 분개
CREATE TABLE journal_entries (
    id                    BIGSERIAL PRIMARY KEY,
    accounting_event_id   BIGINT REFERENCES accounting_events(id),
    raw_transaction_id    BIGINT REFERENCES raw_transactions(id),  -- 동일 tx 분개 그룹핑용 [CR-010]
    entry_date            DATE NOT NULL,
    description           TEXT NOT NULL,
    memo                  TEXT,
    status                VARCHAR(20) NOT NULL DEFAULT 'AUTO_CLASSIFIED',
                          -- AUTO_CLASSIFIED, MANUAL_CLASSIFIED, REVIEW_REQUIRED, APPROVED
    created_at            TIMESTAMP DEFAULT NOW(),
    updated_at            TIMESTAMP DEFAULT NOW()
);

-- 분개 라인 (차변/대변)
CREATE TABLE journal_lines (
    id                BIGSERIAL PRIMARY KEY,
    journal_entry_id  BIGINT REFERENCES journal_entries(id),
    account_code      VARCHAR(50) NOT NULL REFERENCES accounts(code),  -- 계정과목 FK [CR-003]
    account_name      VARCHAR(200) NOT NULL,   -- 계정과목 이름
    debit_amount      NUMERIC(24, 8) DEFAULT 0,  -- 차변 (KRW)
    credit_amount     NUMERIC(24, 8) DEFAULT 0,  -- 대변 (KRW)
    token_symbol      VARCHAR(20),
    token_amount      NUMERIC(38, 18),           -- 토큰 수량
    created_at        TIMESTAMP DEFAULT NOW()
);

-- 계정과목 (Chart of Accounts)
CREATE TABLE accounts (
    id              SERIAL PRIMARY KEY,
    code            VARCHAR(50) UNIQUE NOT NULL,
    name            VARCHAR(200) NOT NULL,
    category        VARCHAR(20) NOT NULL,  -- ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE
    is_system       BOOLEAN DEFAULT FALSE, -- 시스템 기본 계정 여부
    created_at      TIMESTAMP DEFAULT NOW()
);

-- FIFO 원가 로트
CREATE TABLE cost_basis_lots (
    id              BIGSERIAL PRIMARY KEY,
    wallet_address  VARCHAR(42) NOT NULL,
    token_address   VARCHAR(42),           -- null = ETH
    token_symbol    VARCHAR(20) NOT NULL,
    acquisition_date TIMESTAMP NOT NULL,
    quantity        NUMERIC(38, 18) NOT NULL,
    remaining_qty   NUMERIC(38, 18) NOT NULL,
    unit_cost_krw   NUMERIC(24, 8) NOT NULL,
    source_tx_hash  VARCHAR(66) NOT NULL,
    created_at      TIMESTAMP DEFAULT NOW()
);

-- 가격 캐시
CREATE TABLE price_cache (
    id              BIGSERIAL PRIMARY KEY,
    token_address   VARCHAR(42),
    token_symbol    VARCHAR(20) NOT NULL,
    price_date      DATE NOT NULL,
    price_krw       NUMERIC(24, 8) NOT NULL,
    source          VARCHAR(50) NOT NULL,
    fetched_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(token_symbol, price_date, source)
);

-- 감사 추적
CREATE TABLE audit_log (
    id              BIGSERIAL PRIMARY KEY,
    entity_type     VARCHAR(50) NOT NULL,  -- JOURNAL_ENTRY, JOURNAL_LINE, ACCOUNT
    entity_id       BIGINT NOT NULL,
    action          VARCHAR(20) NOT NULL,  -- CREATE, UPDATE, APPROVE
    old_value       JSONB,
    new_value       JSONB,
    created_at      TIMESTAMP DEFAULT NOW()
);

-- 지갑
CREATE TABLE wallets (
    id                SERIAL PRIMARY KEY,
    address           VARCHAR(42) UNIQUE NOT NULL,
    label             VARCHAR(200),
    sync_status       VARCHAR(20) DEFAULT 'PENDING', -- PENDING, SYNCING, COMPLETED, FAILED
    last_synced_at    TIMESTAMP,
    last_synced_block BIGINT,                        -- 재동기화 시 이 블록 이후만 수집 [CR-014]
    created_at        TIMESTAMP DEFAULT NOW()
);
```

### Dependencies

**Backend (Kotlin + Spring Boot)**:
| 의존성 | 용도 |
|--------|------|
| Spring Boot 3.x | 웹 프레임워크 |
| Spring Data JPA | PostgreSQL ORM |
| Flyway | DB 마이그레이션 |
| web3j | Ethereum 트랜잭션 디코딩, ABI 파싱 |
| Ktor Client (또는 WebClient) | Ethereum RPC, CoinGecko API HTTP 호출 |
| Jackson | JSON 파싱 |
| Apache POI | Excel(.xlsx) 내보내기 |
| OpenCSV | CSV 내보내기 |

**Frontend (Next.js + TypeScript)**:
| 의존성 | 용도 |
|--------|------|
| Next.js 14+ | React 프레임워크 |
| TypeScript | 타입 안정성 |
| Tailwind CSS | 스타일링 |
| TanStack Table | 분개 테이블 렌더링 |
| TanStack Query | API 데이터 페칭/캐싱 |
| date-fns | 날짜 포매팅 |

**Infrastructure**:
| 의존성 | 용도 |
|--------|------|
| PostgreSQL 16 | 데이터베이스 |
| Docker + Docker Compose | 로컬 개발 환경 |

**External APIs**:
| API | 용도 | 비용 |
|-----|------|------|
| Ethereum JSON-RPC (eth_getLogs, eth_getBlockByNumber, eth_getTransactionReceipt, eth_getTransactionByHash, eth_blockNumber) | Ethereum 트랜잭션 수집 | RPC 프로바이더에 따라 상이 |
| CoinGecko API | 히스토리컬 토큰 가격 (KRW) | 무료 티어 (30 calls/min) |

## Testing Plan

### Unit Tests

#### 분류 엔진
- [ ] ETH incoming 전송 → `INCOMING` 이벤트로 분류되는지
- [ ] ETH outgoing 전송 → `OUTGOING` + `FEE` 이벤트로 분류되는지
- [ ] ERC-20 Transfer 이벤트 → 올바른 토큰 심볼/수량으로 파싱되는지
- [ ] Uniswap V2 Swap 이벤트 → `SWAP` 이벤트로 분류되고 입출력 토큰이 올바른지
- [ ] Uniswap V3 Swap 이벤트 → `SWAP` 이벤트로 분류되는지
- [ ] 미지원 컨트랙트 호출 → `UNCLASSIFIED`로 분류되는지
- [ ] 실패한 트랜잭션(status=0) → `FEE` 이벤트만 생성되는지

#### 복식부기 엔진
- [ ] 모든 분개의 차변 합계 = 대변 합계인지 (invariant)
- [ ] ETH 수신 → `차변: 자산:암호화폐:ETH / 대변: (상대계정)` 분개 생성
- [ ] ETH 전송 + 가스비 → 2건 분개 생성 (전송 + 가스비)
- [ ] 스왑 → 실현 손익이 FIFO 기준으로 정확히 계산되는지

#### FIFO 원가 추적
- [ ] 동일 토큰 3회 매수 후 일부 매도 → 가장 오래된 로트부터 소진되는지
- [ ] 로트가 부분 소진될 때 remaining_qty가 정확히 업데이트되는지
- [ ] 가스비로 ETH 소비 시 FIFO 로트에서 차감되는지

#### 가격 서비스
- [ ] CoinGecko에서 KRW 가격 조회 성공 시 올바른 값 반환
- [ ] 캐시 히트 시 API 호출 없이 캐시 값 반환
- [ ] 가격 미확인 토큰 → 0 반환 + `UNKNOWN` 플래그 설정

### Integration Tests
- [ ] 지갑 주소 등록 → RPC 호출 → raw_transactions 저장 → accounting_events 생성 → journal_entries 생성까지 전체 파이프라인
- [ ] 분개 수정 → audit_log에 변경 이력 기록
- [ ] 분개 승인 → 상태 변경 및 이후 수정 불가
- [ ] CSV 내보내기 → 파일 내용이 DB 데이터와 일치
- [ ] Excel 내보내기 → 파일이 올바른 .xlsx 형식

### Edge Cases
- [ ] 빈 지갑(트랜잭션 0건) 입력 시 빈 분개 목록 표시
- [ ] 잘못된 주소 형식 입력 시 에러 메시지 표시
- [ ] 동일 지갑 주소 중복 등록 시도 시 기존 데이터 사용
- [ ] CoinGecko API 레이트 리밋 초과 시 graceful degradation (가격 = 0, 플래그 설정)
- [ ] RPC 타임아웃 시 재시도 (최대 3회) 후 에러 표시
- [ ] 가스비가 0인 트랜잭션 (pre-EIP-1559 일부 경우)
- [ ] 동일 블록 내 동일 지갑의 다수 트랜잭션 → 순서 보존
- [ ] 매우 큰 금액 (NUMERIC overflow 방지 확인)
- [ ] self-transfer (from = to = wallet) 처리

## Out of Scope (MVP)

다음 항목은 MVP에서 명시적으로 제외한다:

- 멀티체인 지원 (Polygon, Arbitrum 등) → TODO
- 멀티유저/인증 시스템
- DeFi 프로토콜 지원 (Uniswap V2/V3 외): Aave, Compound, Curve, Lido 등
- 재무제표(B/S, P/L) 생성
- ERP 연동 (SAP, 더존 등)
- 세금 계산/신고서 생성
- 리베이싱 토큰 (stETH, AMPL)
- NFT 밸류에이션
- 에어드롭/더스트 자동 분류
- 플래시론 감지/통합
- 체인 리오그(reorg) 처리
- 실시간 트랜잭션 모니터링 (WebSocket)
- LIFO / 가중평균법 원가 기준
