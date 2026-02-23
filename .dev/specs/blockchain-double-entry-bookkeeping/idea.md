---
title: Blockchain Double-Entry Bookkeeping
date: 2026-02-22
status: clarified
---

## Problem

DAO와 크립토 프로젝트는 투명한 재무 감사가 필요하지만, 블록체인 트랜잭션은 전통적인 회계 형식(복식부기)으로 깔끔하게 변환되지 않는다. 회계사와 세무사는 온체인 데이터를 기반으로 감사 가능한 장부를 만들어야 하지만, 현재 이를 자동화하는 도구가 한국 시장에 없다.

**핵심 사용자**: 회계사/세무사 (한국 시장, K-IFRS 기준)
**핵심 니즈**: 특정 지갑 주소의 블록체인 거래를 복식부기 원장(분개장)으로 변환하여 열람/편집/내보내기

## Solution

Ethereum 블록체인 데이터를 읽어 특정 지갑의 복식부기 원장(Journal Entries)을 자동 생성하는 웹 애플리케이션.

**핵심 기능**:
1. 지갑 주소 입력 → Ethereum 트랜잭션 수집 (표준 Ethereum JSON-RPC: eth_getLogs + eth_getBlockByNumber)
2. 트랜잭션 분류 엔진 — ETH 전송, ERC-20 전송, 가스비, DEX 스왑 등을 회계 이벤트로 변환
3. 복식부기 분개 자동 생성 (차변/대변) with 원화(KRW) 환산
4. 회계사가 검토/수정/승인하는 "Reconciliation" UI
5. 감사 추적(Audit Trail) — 모든 분개는 원본 tx hash, 분류 규칙, 가격 출처에 연결

**아키텍처 (3-Layer)**:
- Layer 1 (Raw): 불변 원본 체인 데이터 (RPC 응답 JSON)
- Layer 2 (Normalized): 분류된 회계 이벤트 (`Incoming`, `Outgoing`, `Swap`, `Fee` 등)
- Layer 3 (Ledger): 복식부기 분개 (차변/대변 Journal Entries)

**MVP 범위** (Phase 1):
- Ethereum 메인넷만
- ETH 전송, ERC-20 전송, 가스비, Uniswap V2/V3 스왑
- FIFO 원가법
- 미분류 거래는 "수동 검토 필요"로 표시
- 멀티체인은 향후 TODO

## Context

**현재 프로젝트 상태**: 빈 저장소. 코드, 설정 파일, 의존성 없음. 기획/브레인스토밍 단계.

**기존 경쟁 서비스**:
| 서비스 | 특징 | 한계 |
|--------|------|------|
| Cryptio | 기관급, ERP 연동 (NetSuite, QuickBooks) | $500+/월, DeFi 지원 부족, 한국 미지원 |
| Bitwave | DeFi/스테이킹 자동 처리, FASB 준수 | 대기업 대상, 고가 |
| Tres Finance | 멀티체인 통합 뷰, 컴플라이언스 자동화 | 모듈화 부족, 한국 미지원 |

**차별화 포인트**: 모듈형 서브원장 API, DeFi 트랜잭션 분류 엔진, K-IFRS 준수 보고서 — 기존 서비스 어디도 이 3가지를 동시에 제공하지 않음.

**기술 스택 후보**:
| 영역 | 후보 | 비고 |
|------|------|------|
| 데이터 수집 | Ethereum JSON-RPC (eth_getLogs + eth_getBlockByNumber) | 표준 RPC, 프로바이더 교체 자유 |
| 트랜잭션 파싱 | viem (TypeScript) | 최신 ABI 디코딩, ethers.js 대안 |
| 원장 엔진 | medici (MongoDB) 또는 Custom PostgreSQL | **미결정 — /specify에서 결정 필요** |
| 가격 데이터 | CoinGecko API + DEX 가격 fallback | KRW 환산 필수 |
| 백필 | ethereum-etl → PostgreSQL | ELT 패턴: 원본 먼저, 변환 나중에 |

**회계 기준**:
- K-IFRS (IAS 38 원가모델) 기본 적용
- FASB ASC 350-60 (공정가치) 경로는 향후 추가
- 계정과목(Chart of Accounts): 커스터마이징 가능한 기본 설정 제공

## Risks

### Critical (5건)
1. **트랜잭션 해석은 M:N 매핑** — 하나의 온체인 tx가 다수의 회계 이벤트를 생성. Uniswap V3 스왑 하나가 라우터 호출, 풀 상호작용, 수수료 로직, 틱 크로싱 등을 포함
2. **히스토리컬 가격 데이터 비신뢰** — 롱테일 토큰은 신뢰할 만한 가격 피드 없음. NFT는 연속 가격 자체가 불존재
3. **DeFi 프로토콜 지원은 무한 스코프** — 수백 개 프로토콜, 각각 고유한 ABI/이벤트/회계 규칙. 플러그인 아키텍처 필수
4. **계정과목 표준 부재** — 크립토용 GAAP/IFRS 가이드라인이 명확하지 않음. LP 포지션이 자산인지, 스테이킹 보상이 수익인지 이자소득인지 등
5. **원가 기준 추적 (FIFO)의 복잡성** — 47개 소스에서 받은 ETH의 가스비 하나에도 가장 오래된 미소비 로트 조회 필요

### Major (9건)
- 회계사의 자동 분개 신뢰 문제 → 감사 추적 + 수동 오버라이드 필수
- "지갑" ≠ "회계 엔티티" — DAO는 다수 주소 사용 → 멀티주소 엔티티 설계 필요
- 가스비 회계 복잡성 — 매 tx마다 ETH 처분 + 자본손익 발생
- 아카이브 노드 접근 비용 — 트레이스 호출 느리고 비쌈
- 관할권별 세법 차이 → 세금 계산은 스코프 밖으로 명확히 구분
- 리베이싱 토큰 (stETH, AMPL) — Transfer 이벤트 없이 잔액 변동
- 에어드롭 vs 더스트 공격 — 온체인 서명이 동일, 자동 분류 불가
- 플래시론 — 수천만 달러 차변/대변이 순액 ~$0
- 스테이블코인 — $1 ≠ 정확히 $1, 미세 변동 노이즈 처리 필요

### Minor (3건)
- 프록시 컨트랙트 업그레이드 시 과거 해석 변경
- MEV 영향 추적은 스코프 밖
- 체인 리오그(reorg) 처리 — finalized 블록만 처리하거나 void-and-rebook 로직

## Debate Summary

**3-model consensus** (Claude + Codex + Gemini 모두 참여)

- **Consensus (합의점)**:
  - 트랜잭션 해석이 핵심 기술 난제 (인제스션이나 원장 엔진이 아님)
  - 3-Layer 아키텍처 필수 (Raw → Normalized → Ledger)
  - MVP 스코프 제한 필수, "미분류"를 일급 개념으로
  - 감사 추적 + 수동 오버라이드 비타협적
  - 플러그인/어댑터 기반 DeFi 프로토콜 지원
  - 가격/밸류에이션 엔진은 핵심 인프라

- **Divergent (이견)**:
  - 원장 엔진: medici(MongoDB) vs Custom PostgreSQL — Gemini는 PostgreSQL 강력 추천 (관계형 데이터, FIFO 스택 쿼리), Codex는 라이브러리 우선 후 교체 가능
  - 제품 프레이밍: 풀 회계 시스템 vs "감사 워크벤치 + 내보내기" (Codex, Gemini 모두 가벼운 시작 권장)
  - 시작 체인: Ethereum(대다수) vs 더 단순한 EVM 체인(Claude)

- **Key Insight**:
  진짜 어려운 건 "코딩"이 아니라 "도메인" — 계정과목, 분류 규칙, 원가법 결정에는 회계 전문가 협업이 필수. Xero/QuickBooks의 "Reconciliation" 패턴처럼 100% 자동화가 아닌 "초안 생성 → 회계사 검토/확인" 워크플로우가 올바른 접근.

상세 토론 내용: [debate.md](./debate.md)

## References

**기존 서비스**:
- [Cryptio](https://cryptio.co) — 기관급 크립토 회계
- [Bitwave](https://www.bitwave.io) — 엔터프라이즈 크립토 회계
- [Tres Finance](https://tres.finance) — Web3 재무 플랫폼

**데이터 소스**:
- [Ethereum JSON-RPC Specification](https://ethereum.github.io/execution-apis/api-documentation/)
- [Etherscan API](https://docs.etherscan.io/)
- [ethereum-etl](https://github.com/blockchain-etl/ethereum-etl)

**회계 기준**:
- [KPMG: Digital Assets under IFRS vs US GAAP](https://kpmg.com/us/en/articles/2024/digital-assets-under-ifrs-accounting-standards.html)
- [FASB ASU 2023-08 / ASC 350-60](https://www.icaew.com/insights/viewpoints-on-the-news/2024/jan-2024/fasb-issues-crypto-asset-standard)
- [Crypto Chart of Accounts in QuickBooks](https://www.theaccountantquits.com/articles/crypto-chart-of-accounts-in-quickbooks)

**오픈소스 라이브러리**:
- [medici (Node.js 복식부기)](https://github.com/flash-oss/medici)
- [hledger (Plain Text Accounting)](https://hledger.org/)
- [viem (TypeScript Ethereum)](https://viem.sh)
- [ethers.js](https://docs.ethers.org/v5/)

## Open Questions

1. **원장 엔진 선택**: medici (MongoDB, 빠른 시작) vs Custom PostgreSQL (관계형 쿼리, FIFO 추적에 유리) — 어떤 것을 선택할 것인가?
2. **제품 프레이밍**: "풀 회계 시스템" vs "감사 워크벤치 + 내보내기" — MVP의 정체성은?
3. **가격 오라클 전략**: CoinGecko API 의존 vs 자체 가격 엔진 구축 vs DEX 온체인 가격 사용 — KRW 환산 소스는?
4. **회계 전문가 협업**: K-IFRS 전문 회계사/세무사와 계정과목 및 분류 규칙을 공동 설계할 것인가?
5. **인증/권한**: 멀티유저 지원 여부, 회계사가 여러 클라이언트의 지갑을 관리하는 시나리오 지원?
6. **데이터 저장**: 사용자 데이터(원장, 수정 이력)의 호스팅 — 클라우드 vs 셀프호스팅?
7. **멀티체인 확장 전략**: EVM 호환 L2 (Arbitrum, Optimism, Base)부터? 비EVM (Solana, Bitcoin)은 언제?
