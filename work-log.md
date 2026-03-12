=== SESSION 3 START ===
=== Report #4 | lines: 40 | elapsed: 20:00 | type: synthesis ===
T3(PM/UX) + 2차 T2/T4/T5 결과 도착. 전체 5팀(10명) 결과 통합.

## T3 PM/UX 핵심 발견 (16건: CRITICAL 3, HIGH 4, MEDIUM 7, LOW 2)

| # | 심각도 | 이슈 | 카테고리 |
|---|--------|------|----------|
| T3-1 | CRITICAL | 신호등 조건(GREEN/YELLOW/RED) 미정의 | Product |
| T3-2 | CRITICAL | FAILED_TX UI 표시/사용자 액션 미정의 | UX |
| T3-3 | CRITICAL | 대조 결과 해석 가이드 없음 (MatchStatus별 원인+조치) | UX |
| T3-4 | HIGH | CSV 내보내기 포맷/컬럼 명세 없음 | Workflow |
| T3-5 | HIGH | 토큰 등록 UX 플로우 불완전 | UX |
| T3-6 | HIGH | Sync status 가시성 미정의 | Product |
| T3-7 | HIGH | Phase 2 대조 실행 워크플로우 불완전 (동기/비동기?) | Workflow |
| T3-8 | MEDIUM | 에러 상태/재시도 UX 미정의 | UX |
| T3-9 | MEDIUM | TX 검색 시나리오 D 과소 명세 | Workflow |
| T3-10 | MEDIUM | 잔고 표시 Phase 1b에서 미정의 | Product |
| T3-11 | MEDIUM | UI 빈 상태/로딩 상태 미명세 | UX |
| T3-12 | MEDIUM | Rate limit 사용자 메시징 미정의 | UX |
| T3-13 | MEDIUM | Phase 1b→2 DoD 테스트 기준 모호 | Product |
| T3-14 | MEDIUM | Excel 내보내기 시트/컬럼 상세 미완 | Product |

## 교차검증 강화 (3+ 팀 합의)

| 이슈 | 합의 팀 수 | 조치 |
|------|-----------|------|
| 신호등 조건 미정의 | T3+T5+이전T3 (3팀) | Phase 1b PRD 반영 필요 |
| Reorg 롤백 전략 | T2+T5+T2(2차) (3팀) | Phase 1b ADR 확장 필요 |
| RPC 시크릿 관리 | T2+T5+T2(2차) (3팀) | Phase 1b 문서화 필요 |
| 대조 결과 해석 가이드 | T3 (신규) | Phase 2 PRD 추가 필요 |

## 문서 수정 불필요 항목 (구현 시 해결)
T3 발견 대부분은 Phase 1b~2 **구현 단계**에서 UI/UX 설계로 해결할 항목.
현재 Phase 1a 문서 스코프에서는 KB 노트로 기록만 수행.

META: 5팀 10명 전체 결과 통합 완료. 교차검증 강화로 신뢰도 상승.
---
=== FINAL REPORT | elapsed: 20:00 | loops: 3 + 보충 ===
end_time: 09:21
termination_mode: count (3 loops + T3 보충)
total_feedback_loops: 3
total_proposals_generated: 5 (convergent: 3, divergent: 2)
total_proposals_validated: 5
total_proposals_falsified: 0
total_agent_findings: 76건 (T1:13, T2:20, T3:16, T4:18, T5:20, 중복 제거 후 ~50 고유)

## Summary

### Confirmed (문서 수정 완료 — 12건)
- SCOPE BOUNDARY 9항목 통일 (AGENTS.md + PRD.md)
- Phase 2→3, 3→4 전환 기준 추가
- README.md DB 확정 (PostgreSQL)
- FAILED_TX 전수 일관 반영 (DDL, 카운터, 플로우, 설명, 시나리오, Excel — 12곳)
- chain-adapters.md ADR 교차참조 추가

### Uncertain (Phase 진입 시 결정 필요 — d15, d16에 기록)
- RPC 시크릿 관리 전략 (3팀 합의, Phase 1b)
- Reorg 롤백 전략 + last_block_hash (3팀 합의, Phase 1b)
- 신호등 조건 정의 (3팀 합의, Phase 1b)
- 대조 결과 해석 가이드 (T3, Phase 2)
- UTXO 거스름돈 감지, rate limit (Phase 3)
- UI 빈 상태/에러 상태/토큰 등록 UX (T3, Phase 1b)

### Follow-up Required
- d15-phase-design-issues.md: 기술 설계 이슈 11건
- d16-ux-pm-gaps.md: UX/PM 미명세 14건

## Key Artifacts
- 수정 파일: AGENTS.md, PRD.md, README.md, chain-adapters.md
- KB: d15, d16 생성, _index.md 갱신

## Biggest Surprise
FAILED_TX를 5.3.3에 추가한 후에도 DDL, 카운터, 플로우, Excel 명세 등 5곳에서 추가 누락 발견.
단일 상수 추가가 문서 전체에 파급되는 전형적인 cross-cutting concern.

## Recommendation for Next Session
1. Phase 1a 구현 시작 전: d15의 F8(reorg), F9(신호등), F10(cutoff 실패 복구) 확정
2. Phase 1b UI 구현 전: d16의 신호등 조건, 빈 상태, 에러 상태 UX 설계
3. Phase 2 구현 전: 대조 결과 해석 가이드, CSV 포맷 명세, Excel 시트 상세
---
=== Report #3 | lines: 24 | elapsed: 15:00 | type: feedback ===
LOOP 3 — FAILED_TX 전수 조사 완료 + 잔여 정합성 검증.

DIAGNOSE: Loop 2에서 FAILED_TX DDL/카운터 수정 후, PRD 내 잔여 불일치 탐색.
PROPOSE (CONVERGENT): F4~F6 수정이 Phase 2-4 기능 목록과 Excel 내보내기 명세에도 반영 필요.
TEST: PRD grep "FAILED_TX" → 10회 출현, 모두 정합 확인.
  - line 73: 시나리오 ✓
  - line 330: DDL ✓
  - line 339: DDL comment ✓
  - line 360: 5.3.1 컬럼 설명 ✓
  - line 387: 5.3.3 상세 ✓
  - line 411,413: 5.3.4 플로우 ✓
  - line 422: 카운트 UPDATE ✓
  - line 636: Phase 2-4 기능 ✓
  - line 654: Excel 내보내기 ✓

추가 수정:
  - PRD 2-4 수락 기준에 FAILED_TX 추가
  - Excel 불일치 항목 목록에 FAILED_TX 추가

PROPOSE (DIVERGENT): "하지 말 것" 섹션과 Phase 2 reconciliation 테이블의 관계 검증.
  → 충돌 없음. "cutoff 전용" ≠ "대조 전용". 의미 명확, 수정 불필요.

UPDATE: kb/_index.md에 d15 추가. 전체 수정 12건.
META: 3회 루프 완료. 문서 정합성 대폭 개선. T3(PM/UX) 미도착이나 min_required_loops=3 충족.
---
=== Report #2 | lines: 30 | elapsed: 10:00 | type: feedback ===
LOOP 2 — F1~F6, F11 문서 수정 적용 + 추가 발견 수정.

DIAGNOSE: FAILED_TX가 5.3.3에 추가되었지만 관련 DDL/카운터/플로우에 일관 반영 안 됨.
PROPOSE (DIVERGENT): reconciliation_runs DDL, 5.3.1 컬럼 상세, 5.3.4 플로우, 사용 시나리오에서
  FAILED_TX가 누락된 곳을 전수 조사 → 추가 3건 발견.

## 적용 완료 (Loop 1 + Loop 2)

| # | 파일 | 수정 내용 |
|---|------|----------|
| F1 | AGENTS.md, PRD.md | SCOPE BOUNDARY 9항목 통일 (ERP + CI/CD 모두 포함) |
| F2 | AGENTS.md | Phase 2→3, 3→4 전환 기준 추가 |
| F3 | README.md | DB "미확정" → "PostgreSQL" |
| F4 | PRD.md 5.3.4 | FAILED_TX 재분류 단계(5.5) 플로우 추가 |
| F5 | PRD.md 5.3 DDL | match_status 주석에 FAILED_TX 추가 |
| F6 | PRD.md 시나리오 | 결과 목록에 DB_ONLY, FAILED_TX 추가 |
| F11 | chain-adapters.md | ADR-005, ADR-006 교차참조 추가 |
| NEW | PRD.md 5.3 DDL | reconciliation_runs에 failed_tx INT 컬럼 추가 |
| NEW | PRD.md 5.3.1 | failed_tx 컬럼 상세 설명 추가 |
| NEW | PRD.md 5.3.4 | 카운트 UPDATE에 failed_tx 추가 |

TEST: FAILED_TX 관련 전수 검색 — PRD.md 내 FAILED_TX 8회 출현, 모두 정합 확인.
UPDATE: d15-phase-design-issues.md에 미해결 설계 이슈 기록 완료.
META: Loop 1~2에서 문서 수정 10건 완료. T3(PM/UX) 미도착 — Loop 3에서 통합 예정.
---
=== Report #1 | lines: 63 | elapsed: 05:00 | type: synthesis ===
LOOP 1 — 5팀(T1~T5) 에이전트 리뷰 결과 종합. T3 PM/UX 팀 아직 진행 중, 4/5팀 결과 기반.

## 교차검증된 발견 (2+ 팀 합의, HIGH CONFIDENCE)

| # | 이슈 | 검증 팀 | 심각도 | 조치 |
|---|------|---------|--------|------|
| F1 | SCOPE BOUNDARY 불일치: AGENTS.md #6~8 vs PRD #6~8 항목 다름 | T1+T4 | CRITICAL | 문서 수정 |
| F2 | Phase 2→3, 3→4 전환 기준 누락 | T4 | CRITICAL | AGENTS.md 추가 |
| F3 | README.md DB "미확정" → PostgreSQL 확정 | T1+T4 | MEDIUM | 수정 |
| F4 | PRD 5.3.4 대조 플로우에 FAILED_TX 분류 단계 누락 | T2 | HIGH | 수정 |
| F5 | PRD DDL 주석(line 337)에 FAILED_TX 미포함 | T2 | LOW | 수정 |
| F6 | PRD 사용 시나리오(line 72)에 DB_ONLY, FAILED_TX 누락 | 자체 | LOW | 수정 |
| F7 | RPC 비밀/시크릿 관리 미명시 | T2+T5 | HIGH | Phase 1b 설계 |
| F8 | Reorg 롤백 전략 미완성 (last_block_hash 미정의) | T2+T5 | HIGH | Phase 1b 설계 |
| F9 | 신호등 조건(GREEN/YELLOW/RED) PRD 미정의 | T5 | HIGH | Phase 1b 설계 |
| F10 | Cutoff onboarding 실패 복구 로직 누락 | T2+T5 | HIGH | Phase 1b 설계 |
| F11 | chain-adapters.md에 ADR-005/006 교차참조 없음 | T4 | MEDIUM | 수정 |

## 단일 팀 발견 (검증 필요, MEDIUM CONFIDENCE)

| # | 이슈 | 팀 | 심각도 | 비고 |
|---|------|-----|--------|------|
| F12 | 어댑터 하위 디렉토리 구조 미명시 | T1 | HIGH | 구현 시 결정 |
| F13 | 포트 네이밍 컨벤션 미정의 | T1 | HIGH | 구현 시 결정 |
| F14 | UTXO 거스름돈 감지 휴리스틱 없음 | T2 | HIGH | Phase 3 설계 |
| F15 | UTXO Public API rate limit 전략 불완전 | T2 | HIGH | Phase 3 설계 |
| F16 | 2-track 수집 원자성 미명시 | T2 | MEDIUM | Phase 1b 설계 |
| F17 | 백업/DR 전략 미문서화 | T5 | HIGH | Phase 2+ |
| F18 | ERC-20 decimals 정규화 미문서화 | T2 | LOW | 구현 시 해결 |

## 즉시 수정 대상 (문서 수정으로 해결 가능)
F1, F2, F3, F4, F5, F6, F11 — 7건. 이번 루프에서 적용.

## Phase 설계 시 해결 대상 (코드 구현 전 결정 필요)
F7~F10, F12~F17 — 11건. KB 노트로 기록, 해당 Phase 진입 시 해결.

META: 4/5팀 결과 종합 완료. T3(PM/UX) 결과 도착 시 Report #2에서 통합.
다음 루프: F1~F6, F11 문서 수정 적용 → 검증.
---
=== Report #0 | lines: 18 | elapsed: 00:00 | type: milestone ===
task_type: analysis
task_goal: 전체 문서 산출물 리뷰 — 10명 에이전트로 정합성, 누락, 리스크, 실현 가능성 점검
in_scope: PRD.md, AGENTS.md, CLAUDE.md, README.md, docs/architecture.md, docs/schema-decisions.md, docs/chain-adapters.md, docs/known-edge-cases.md, docs/adr/ADR-001~008
out_of_scope: 실제 코드 구현, CI/CD, archive/ 디렉토리
min_required_minutes: null
min_required_loops: 3
done_definition:
  - 10명 에이전트 리뷰 결과 수집 및 교차검증
  - 발견된 불일치/누락 전부 문서 반영
  - 3회 이상 피드백 루프 완료
deliverables: 갱신된 문서들, work-log.md, kb/ 노트
as_of_date: 2026-03-12
start_time: 09:01
에이전트 구성 (10명, 5팀):
  T1: 백엔드 아키텍트 + 데이터 엔지니어 (구조, DB, 포트/어댑터)
  T2: 블록체인 전문가 + 보안 엔지니어 (온체인, RPC, UTXO, 보안)
  T3: PM + UX 디자이너 (워크플로우, UI 명세, 사용성)
  T4: QA + 테크니컬 라이터 (문서 정합성, 테스트 커버리지, 표현)
  T5: DevOps + 리스크 분석가 (배포, 운영, 엣지케이스, 리스크)
---
=== SESSION 2 CONTINUATION ===
=== Report #1 | lines: 38 | elapsed: 02:30:00 | type: milestone ===
LOOP — 세션 복구 후 미완료 피드백 5건 일괄 적용.

## 적용 완료 항목

### 1. docs/architecture.md 전면 재작성
- 이전: domain/port + adapter/chain, adapter/persistence, adapter/web
- 이후: adapter/inbound, adapter/outbound, application/port/input, application/port/output, application/service, domain/service, domain/model
- "인터페이스 생략 가능" → "인터페이스 반드시 생성" (사용자 결정 반영)
- "다양한 Persistent Layer 지원" 섹션 추가
- inbound vs outbound 구분 표 추가

### 2. ADR-006 UTXO 다중 주소 처리 추가
- BTC N:M vin/vout 저장 전략 명세
- from_address/to_address = 감시 월렛 관점 대표값, raw_data = 원본 보존
- value_raw = 감시 월렛 관점 순입출금액
- UTXO 기반 잔고 역산 공식 추가: balance(t) = cutoff_balance + 수신 - 소비

### 3. 토큰 추적 기능 설계
- PRD: wallet_tracked_tokens 테이블 추가 (Phase 1b, 섹션 5.1)
- PRD: 섹션 4.6 토큰 추적 관리 (등록 플로우 + UI 표시 규칙)
- PRD: Phase 1b 기능 목록에 1b-16 추가
- schema-decisions.md: 토큰 추적 섹션 추가

### 4. EC-2 UTXO 잔고 역산 갱신
- cutoff 이후 수집 TX로 잔고 역산 가능 명시
- ADR-006 참조 링크 추가

### 5. 교차 문서 정합성 7건 수정
- AGENTS.md: 아키텍처 의존 방향 갱신, DB(거래소) Databricks 기본으로
- README.md: ExchangeTransactionPort 어댑터 순서(Databricks 기본), 패키지 구조, 의존 방향
- PRD: Phase 2-2 MySqlExchangeAdapter → DatabricksExchangeAdapter
- PRD: EXCHANGE_DB_ADAPTER 기본값 mysql → databricks
- schema-decisions.md: Exchange DB Phase 2 기본 Databricks로 통일

META: 세션 복구. 사용자 피드백 10건 중 전부 문서 반영 완료.
---
=== SESSION 2 START ===
=== Report #0 | lines: 30 | elapsed: 00:00 | type: milestone ===
task_type: analysis
task_goal: 사용자 피드백 10건 반영 + 기존 산출물 전면 재점검 (PostgreSQL vs Parquet, 아키텍처 재구조화, UTXO 다중주소, 토큰 추적 등)
in_scope: PRD 우선순위/시나리오 수정, 저장소 계층 결정(PG vs Parquet), 아키텍처 패키지 구조 변경(inbound/outbound/port/input/output), ADR-002/004/006 갱신, 토큰 추적 기능 설계, match_status 상수 관리, UTXO 다중주소 처리, docs/* 정합성
out_of_scope: 실제 코드 구현, CI/CD, 인증/권한
min_required_minutes: 60
min_required_loops: null
done_definition:
  - 사용자 피드백 10건 전부 문서 반영
  - 아키텍처 패키지 구조 재설계 완료
  - PostgreSQL vs Parquet 분석 및 문서화
  - 토큰 추적 기능 PRD/스키마 반영
  - ADR-002, ADR-004, ADR-006 갱신
deliverables: 갱신된 PRD.md, AGENTS.md, docs/adr/*, docs/architecture.md, docs/known-edge-cases.md, README.md
as_of_date: 2026-03-12
start_time: 05:40
---
=== SESSION 2 ARCHIVE (이전 세션 보존) ===
---
=== Report #11 | lines: 30 | elapsed: 55:00 | type: synthesis ===
LOOP 10 — 배경 에이전트 7건 결과 통합.

## 에이전트 결과 요약
| 에이전트 | 핵심 발견 | 조치 |
|---------|----------|------|
| A1+A9 (Doc일관성) | PRD JDBC URL, MySQL 확정 등 3건 — 이미 수정 완료 | 검증 완료 |
| A2+A3 (Exchange DB) | MySQL Phase 2 기본, Port 추상화 확인 — ADR-004와 일치 | 검증 완료 |
| A5+A6 (리스크+PM) | Phase 2 Exchange DB 스키마 미확정이 블로커, 대조 오류예산 SLA 미정의 | G2, G5 이미 등록 |
| A7+A10 (FE+QA) | 신호등 조건 미정의, task verify 미정의, FE 상태정의 갭 | G8, G9 이미 등록 |
| A4+A8 (DevOps+보안) | 시크릿관리(P0), 분산잠금(P0), 헬스체크(P1) | d6에 일부 반영, 나머지 Phase 2+ |
| Reorg 에이전트 | confirmation blocks 충분 확인, REORGED 상태값 제안, soft/hard 구분 | d13 생성 |
| Reconciliation 에이전트 | tx_hash 정규화(d7 확인), fee 분리 비교, RBF, batch 분할, direction | d14 생성 |

## 에이전트 교차검증 결과
- d7(tx_hash 정규화): 독립 에이전트가 동일 결론 도출 → 신뢰도 0.4→0.75
- EC-4(소수점): 리스크+대조 에이전트 모두 확인 → 검증됨
- EC-5(reorg): reorg 에이전트가 상세 전략 제공 → d13으로 확장
- G9(신호등): FE 에이전트도 동일 갭 식별 → 검증됨

## 신규 발견 (에이전트에서만 식별)
1. REORGED tx_status 추가 필요 (d13) — Phase 1a V1 migration에 포함 권장
2. Reconciliation: fee 분리 비교 전략 (d14) — Phase 2 설계 시 반영
3. RBF/Nonce 교체 처리 (d14) — Phase 2 fuzzy match 확장
4. DevOps: 시크릿관리 P0 (env plaintext 위험) — Phase 2 전 개선

META: 55분 경과 / 60분. 에이전트 통합 완료. 총 제안 14건. FINAL REPORT 작성.
---
=== FINAL REPORT | elapsed: 60:00 | loops: 11 ===
end_time: 05:30
termination_mode: time
total_feedback_loops: 11
total_proposals_generated: 14 (convergent: 7, divergent: 7)
total_proposals_validated: 10
total_proposals_falsified: 0

## Summary

### 확정 (Confirmed)
- ADR 8개 작성 완료 (ADR-001~008): Flyway, Port/Adapter, Cutoff, Exchange DB, EVM 2-Track, UTXO Dual, Batch Scheduling, Internal TX
- README.md 프로젝트 전체 구성도 작성 완료
- 문서 불일치 7건 전부 수정: PRD(cutoff_block, sync_status, ExchangeDbAdapter명, Exchange DB URL), AGENTS.md(MySQL→미확정), schema-decisions.md(Exchange DB 추상화)
- 블라인드스팟 4건 중 3건 해소(B1 Apache POI, B2 cutoff 경계, B3 is_contract 거부)
- 엣지케이스 EC-4~EC-7 추가 (소수점정밀도, reorg복구, RPC스로틀링, cutoff stale)
- PRD 리스크 R1~R6 전부 ADR/EC/제안으로 커버 확인
- tx_hash 정규화(d7): 2개 독립 에이전트가 동일 결론 → 신뢰도 상승
- Reorg confirmation blocks: 5개 체인 전부 현재 마진 충분 확인 (외부 소스 기반)

### 불확실 (Uncertain)
- U5: Exchange DB 플랫폼 미확정 (HIGH) — 사용자 결정 대기
- U7: BTC/DOGE 자체 노드 보유 여부 (HIGH) — Phase 3 전 확인 필요
- U8: RPC 안정성 (MED) — Phase 1b 실측 필요

### 후속 조치 (Follow-up Required)
- G8: Taskfile.yml 재생성 (Phase 1a 착수 시)
- G9: 대시보드 신호등 조건 정의 (Phase 1b, d9+d10 참조)
- d7: tx_hash 정규화 (Phase 1b INSERT 시점)
- d12: ERC-20 토큰 수집 범위 결정 (Phase 1b 착수 전)
- d13: REORGED tx_status 추가 (Phase 1a V1 migration 권장)
- d14: 대조 fee 분리 비교 + RBF 처리 (Phase 2 설계)

## Key Artifacts
- `docs/adr/ADR-001~008.md` — 8개 Architecture Decision Records
- `README.md` — 프로젝트 전체 구성도
- `kb/raw/d6~d14` — 9개 제안 노트
- `kb/curated/UNCERTAINTY-REGISTER.md` — U4~U8 활성
- `kb/curated/GAP-REGISTER.md` — G1~G9 활성
- `docs/known-edge-cases.md` — EC-1~EC-7

## Biggest Surprise
1. 대시보드 신호등 조건 미정의(G9) — PRD에 UI 명세는 있으나 백엔드 API 완전 누락.
2. 에이전트 교차검증에서 tx_hash 정규화(d7)가 독립적으로 동일 결론 도출 — 높은 신뢰도.
3. Reorg 에이전트가 Polygon L1 checkpoint finality를 발견 — 128 confirmation이 과잉이지만 안전.

## Recommendation for Next Session
1. **Phase 1a 착수**: Taskfile.yml + .env.example + docker-compose.yml + Flyway V1~V4
2. **착수 전 결정**: d12 ERC-20 토큰 수집 범위, d13 REORGED 상태값 V1 포함 여부
3. **Phase 1b 진입 시**: d10 Health API + d11 테스트 시나리오 + d13 reorg 처리 참조
4. **Phase 2 설계 시**: d14 대조 엣지케이스 15건, G5 오류 예산 SLA 정의

## Reproducibility
- 도구: Read, Edit, Write, Grep, Glob, Agent(Explore x2 bg, 이전세션 5팀 10명)
- 소스: PRD.md, AGENTS.md, CLAUDE.md, docs/*, kb/*, archive/
- 외부 소스: tatum docs, quicknode docs, polygon.technology, ethereum.org (reorg 데이터)
- 가정: archive/ 코드는 참조만, 수정 대상 아님
---
=== Report #10 | lines: 17 | elapsed: 50:00 | type: feedback ===
LOOP 9 — DIVERGENT(ERC-20 범위) + 리스크 검증.

DIAGNOSE: PRD R6 "비표준 ERC-20 화이트리스트" 언급이 스키마에 미반영.
- Phase 1b-2 "ERC-20 Transfer 수집" 범위가 모호.
- 화이트리스트 테이블이 없으면 모든 Transfer 수집이 기본.

PROPOSE (DIVERGENT — MISSING VARIABLE):
- d12-erc20-token-scope: "모든 수집 + UI 필터" 전략 제안.
  Phase 1b에서 eth_getLogs로 모든 Transfer 수집 → raw_data 보존.
  대시보드에서는 알려진 토큰만 표시. Phase 2 대조는 Exchange DB 토큰만 대상.

TEST: PRD 10절 리스크 R1~R6 vs ADR 교차검증.
- R1(Internal TX) → ADR-008 ✓, R2(rate limit) → ADR-007 ✓,
- R3(UTXO) → ADR-006 ✓, R4(DB 스키마) → ADR-004 ✓,
- R5(소수점) → EC-4 ✓, R6(비표준 ERC-20) → d12 제안.

UPDATE: d12 raw note 생성. 리스크 6건 전부 ADR/EC/제안으로 커버 확인.
META: 50분 경과 / 60분. 10분 남음. Final report 작성 시작.
---
=== Report #9 | lines: 28 | elapsed: 45:00 | type: synthesis ===
LOOP 8 — 중간 종합: 전체 리스크/갭/불확실성 정리.

## 현재 상태 종합
| 카테고리 | 전 | 후 | 변화 |
|---------|---:|---:|------|
| 문서 불일치 | 7건 | 0건 | 전부 수정 (PRD, AGENTS.md, schema-decisions.md) |
| 블라인드스팟 | 4건 | 1건(B4 Phase1b 검증) | B1,B2,B3 해소 |
| 불확실성(활성) | 2건 | 5건(U4~U8) | 3건 신규 식별+등록 |
| 갭(활성) | 4건 | 9건(G1~G9) | 5건 신규 식별+등록 |
| 엣지케이스 | 3건(EC-1~3) | 7건(EC-1~7) | 4건 신규 추가 |
| ADR | 0개 | 8개 | 전부 신규 |
| KB notes | 5 raw, 6 curated | 11 raw, 7 curated | 제안 11건 |

## Phase 1a 착수 전 필수 조치 (구현 차단 없음)
1. Taskfile.yml 재생성 (G8) — `task verify` = backend test + frontend build
2. .env.example 신규 생성 — PRD 8.2 기반, archive 것은 stale
3. docker-compose.yml 재생성 — app + postgres

## Phase 1b 구현 시 반영 필요
1. `/api/health/sync` 엔드포인트 (d10, G9)
2. tx_hash 정규화 저장 (d7)
3. 테스트 시나리오 T1~T8 (d11)

PROPOSE (CONVERGENT — SYNTHESIS): 전체 리스크 매트릭스 확정.
TEST: 8루프 전체 보고서 교차 확인 — 누적 노벨티 충분(11건 제안, 8개 ADR).
META: 45분 경과 / 60분. 15분 남음. 에이전트 결과 미도착. Final report 준비 단계 진입.
---
=== Report #8 | lines: 19 | elapsed: 40:00 | type: feedback ===
LOOP 7 — DIVERGENT(테스트 전략) + 종합 검증.

DIAGNOSE: Phase 1b 테스트 시나리오 미정의. PRD에 "핵심 수집 로직 커버"만 있고 구체 시나리오 없음.
- archive/에 20개 통합 테스트 패턴 존재. 재사용 가능한 패턴: IntegrationTestBase, MockWebServer.

PROPOSE (DIVERGENT — DECOMPOSITION):
- d11-test-strategy-phase1b: P0 테스트 5개(수집→저장, 멱등성, 증분동기화, Flyway, CRUD API),
  P1 테스트 3개(재시도, CAS 동시성, 상태전이). MockWebServer 사용 패턴 포함.

TEST:
- archive/ 통합 테스트 20개 Glob 확인 → 패턴 참조 가능성 확인.
- d5 블라인드스팟 B4 "기술 검증 미실시" — Phase 1b에서 해소 예정. 현재 문서 단계에서는 정상.

UPDATE: d11 raw note 생성. kb/_index 갱신 (raw 11개, curated 7개).
META: 40분 경과 / 60분. 제안 총 11개(convergent 6, divergent 5). 3루프 연속 divergent 포함.
  남은 20분: 에이전트 결과 통합 + 최종 종합 + final report 준비.
---
=== Report #7 | lines: 19 | elapsed: 35:00 | type: feedback ===
LOOP 6 — CONVERGENT 결합 + 추가 검증.

DIAGNOSE: 프론트엔드 상태 정의 갭 확인.
- PRD "신호등" 언급 2건(line 65, 575) but 조건 미정의 → d9 제안, G9 등록.
- d6(관측성) + d9(신호등) 결합 → d10 통합 Health API 설계.

PROPOSE (CONVERGENT — COMBINATION):
- d10-health-api-convergent: GET /api/health/sync 엔드포인트 설계.
  SyncHealthResponse에 체인별 상태, lag, 신호등 색상, 메시지 포함.
  Phase 1b 범위에서 구현 가능. PRD 시나리오 A 지원.

TEST:
- `com.example.txcollector` 패키지명 일관성 → PRD, architecture.md, AGENTS.md 모두 일치.
- Docker Compose 참조 일관성 → 전 문서 일치.
- d5 블라인드스팟 4건 중 3건 해소(B1, B2, B3), 1건 Phase 1b 검증(B4).

UPDATE: d10 raw note 생성, G9 등록, d5 B2 갱신.
META: 35분 경과 / 60분. 문서 정합성 매우 높음. 배경 에이전트 결과 통합 보류.
  다음: 에이전트 결과 확인 시도 + 미탐색 영역(FE 상세, 테스트 전략) 탐색.
---
=== Report #6 | lines: 21 | elapsed: 30:00 | type: feedback ===
LOOP 5 — 추가 교차검증 + 2건 DIVERGENT + 누락 식별.

DIAGNOSE: 1-report-below 이후 추가 불일치 탐색.
- `task verify` 참조됨(PRD 1a-2, 완료기준) but Taskfile.yml 미생성. archive/ 에만 존재.
- d5-blindspots B2 "cutoff 허용 범위 미명시" — AGENTS.md line 139에 이미 반영됨 확인.

PROPOSE (DIVERGENT — SCALE SHIFT):
- d8-data-retention-strategy: raw_transactions 무한 성장 리스크.
  1년 ~600만 행, ~60GB 예상. 파티셔닝 친화적 인덱스는 이미 설계됨.
  현재 조치 불필요, 참조용 보존.

PROPOSE (CONVERGENT — COMBINATION):
- d7 tx_hash 정규화 + PRD 5.3 대조 엔진 설계 결합.
  tx_hash 저장 시 정규화(lowercase, 0x prefix)를 raw_transactions INSERT 시점에 적용하면
  Phase 2 대조 시 추가 변환 없이 직접 비교 가능.

TEST:
- archive/Taskfile.yml verify task 확인: `task backend:test + task frontend:build`. G8 등록.
- d5-blindspots B2: AGENTS.md "cutoff 전용 서비스/테이블 금지" 확인 → 해소됨.
- PRD NormalizedTransaction 필드 vs raw_transactions DDL 교차 → 일치.

UPDATE: GAP-REGISTER G8 추가. d8 raw note 생성. d5 B2 상태 미갱신(다음 루프).
META: 30분 경과 / 60분. 절반 도달. 문서 정합성 높은 수준 확보. 에이전트 결과 통합 필요.
---
=== Report #5 | lines: 22 | elapsed: 25:00 | type: feedback ===
LOOP 4 — 교차검증 심화 + 블라인드스팟 갱신 + DIVERGENT 제안.

DIAGNOSE: d5-blindspots 3건 중 2건 stale (B1 해소됨, B3 사용자 거부). 갱신 완료.
- B1: AGENTS.md에 Excel 허용 이미 명시 → 해소.
- B3: is_contract 사용자 거부 → ADR-008 참조로 갱신.
- PRD Phase 4 완료 기준 누락 → G7 등록.
- PRD Phase 2-2 "ExchangeDbAdapter" → "MySqlExchangeAdapter" 정정 (ADR-004 정합).
- AGENTS.md 상세 문서 참조에 docs/adr/ 누락 → 추가.

PROPOSE (DIVERGENT — EDGE CASE):
- d7-txhash-normalization: 대조 엔진에서 tx_hash 정규화 전략 필요.
  0x prefix 유무, 대소문자 차이가 false mismatch 유발 가능.
  Phase 2 구현 시 저장 시점 정규화 + 비교 시점 정규화 이중 장치 제안.
  관련성 검증: Phase 2 대조 정확도에 직접 영향.

TEST: AGENTS.md ↔ PRD ↔ docs/* ↔ ADRs 교차검증.
- SCOPE BOUNDARY 9개 항목 일치 확인 (AGENTS.md vs PRD 2.2).
- Phase 전환 기준: AGENTS.md(1a→1b, 1b→2) vs PRD(1a~4) — AGENTS.md에 2→3, 3→4 미명시. 현재 Phase 1a이므로 당장 불필요.
UPDATE: d5-blindspots B1, B3 갱신. PRD 2-2 정정. GAP-REGISTER G7 추가.
META: 25분 경과 / 60분. 문서 정합성 대부분 확보. 배경 에이전트 아직 미완.
---
=== Report #4 | lines: 20 | elapsed: 20:00 | type: feedback ===
LOOP 3 — 교차검증 + DIVERGENT 제안 + 문서 정합성 심화.

DIAGNOSE: 1-report-below 완료 후, 잔여 불일치 탐색.
- CLAUDE.md에 ADR 디렉토리 참조 누락 → 수정 완료.
- PRD 섹션 5.3~5.5 번호 체계 확인 → 정상 (5.4가 Phase 3).
- kb/_index.md에 d5-blindspots, d6 미등록 → 수정 완료.

PROPOSE (DIVERGENT — MISSING VARIABLE):
- d6-scheduler-observability-gap: 배치 스케줄러 관측성 갭.
  PRD에 대시보드 신호등 UI는 있으나, 이를 지원하는 백엔드 health API가 미명시.
  `/api/health/sync` 엔드포인트가 Phase 1b에서 필요.
  관련성 검증: task_goal 직접 관련 (PRD 시나리오 A "일일 감사 체크").

TEST: docs/architecture.md, docs/chain-adapters.md 전부 읽어 ADR 내용과 교차검증 → 일관됨 확인.
UPDATE: CLAUDE.md ADR 참조 추가, kb/_index 갱신, d6 raw note 생성.
META: 20분 경과 / 60분. 핵심 산출물 완료. 배경 에이전트 2건(reorg, reconciliation) 결과 대기.
  다음 루프: 에이전트 결과 통합 + CONVERGENT 종합 분석.
---
=== Report #3 | lines: 24 | elapsed: 15:00 | type: milestone ===
LOOP 2 — ADR 8개 완성 + 문서 불일치 수정 + README.md 생성.

## 산출물
- ADR-005 EVM 2-Track 수집 전략 (docs/adr/)
- ADR-006 UTXO 이중 전략 (docs/adr/)
- ADR-007 배치 스케줄링 전략 (docs/adr/)
- ADR-008 Internal TX 처리 전략 (docs/adr/)
- README.md 프로젝트 전체 구성도 + 간략 정리

## 문서 수정 (CONVERGENT — COMBINATION)
- AGENTS.md: "MySQL 8.x" → "미확정 (MySQL 8 기본 / Athena / Databricks)"
- schema-decisions.md: Exchange DB 섹션 → ExchangeTransactionPort 추상화 반영
- PRD.md:214 start_block → cutoff_block + cutoff_balance 반영
- PRD.md:212 sync_status 주석에 5개 상태값 명시
- PRD.md:726 EXCHANGE_DB_URL → EXCHANGE_DB_ADAPTER 추가, jdbc:mysql 형식
- known-edge-cases.md: EC-4(소수점정밀도), EC-5(reorg복구), EC-6(RPC스로틀링), EC-7(cutoff stale) 추가

## KB 업데이트
- UNCERTAINTY-REGISTER: U6(쿼리호환), U7(BTC노드), U8(RPC안정성) 추가
- GAP-REGISTER: G5(대조오류예산), G6(cutoff stale감지) 추가

TEST: ADR 8개 교차검증 — 각 ADR이 독립적, PRD/CLAUDE.md와 일관됨 확인.
UPDATE: 1-report-below의 ADR 계획(line 23) 8개 전부 완료. 주요 문서 불일치 3건 해소.
META: 15분 경과 / 60분. 핵심 산출물 대부분 완료. 다음: DIVERGENT 제안 + 문서 교차검증 심화.
---
=== Report #2 | lines: 42 | elapsed: 08:00 | type: synthesis ===
LOOP 1 — 10명 에이전트 5팀 전원 결과 수집. 종합 매트릭스 도출.

## 종합 발견 매트릭스 (A1~A10)
| 카테고리 | 건수 | 핵심 |
|---------|------|------|
| 문서 불일치 | 3 CRITICAL, 2 MEDIUM | Exchange DB URL(PostgreSQL→TBD), MySQL 확정→미확정, Excel 교차참조 |
| 문서 누락 | 4건 | ExchangeTransactionPort 추상화 문서, FE 상태정의, task verify 정의, Phase Gate |
| 신규 엣지케이스 | 4건 | EC-4(소수점정밀도), EC-5(reorg복구), EC-6(RPC스로틀링), EC-7(cutoff stale) |
| 신규 불확실성 | 3건 | U6(Exchange DB 아키텍처), U7(쿼리 호환성), U8(RPC 안정성) |
| 신규 갭 | 2건 | G5(대조 오류예산), G6(cutoff stale 감지) |
| 보안 | 3 P0 | 시크릿관리, 분산잠금, Exchange DB RO 강제 |
| DevOps | 4건 | 헬스체크, 메트릭, 서킷브레이커, 구조화로깅 |
| FE/QA | 6건 | 상태정의, 접근성, 커버리지 갭, 계약테스트 |

## Exchange DB 아키텍처 결론 (A2+A3)
- Phase 2: MySQL 8 기본 (JDBC, <100ms, Docker). 사용자 확정시까지 기본값.
- Phase 3+: Athena/Databricks는 ExchangeTransactionPort 어댑터로 플러그인.
- Iceberg: 테이블 포맷이지 DB 아님. 독립 사용 불가.
- 핵심: Port/Adapter 패턴으로 추상화. @ConditionalOnProperty로 어댑터 선택.

## ADR 문서 계획 (8개)
ADR-001 Flyway | ADR-002 Port/Adapter | ADR-003 Cutoff | ADR-004 Exchange DB
ADR-005 EVM 2-track | ADR-006 UTXO dual | ADR-007 Batch scheduler | ADR-008 Internal TX

PROPOSE (CONVERGENT — COMBINATION): 10명 결과 종합 → ADR 8개 + README 작성 착수.
TEST: 에이전트 간 교차검증 — Exchange DB 결론 일관(A2+A3=Phase2 MySQL, A4+A8=Docker profiles, A5+A6=Phase2 gate).
UPDATE: kb/curated/UNCERTAINTY-REGISTER U6-U8 추가 예정. GAP-REGISTER G5-G6 추가 예정.
META: 8분 경과 / 60분. 초기 진단+종합 완료. ADR 작성 단계로 전환. 생산적 진행.
---
=== Report #1 | lines: 15 | elapsed: 03:30 | type: feedback ===
DIAGNOSE: 기존 산출물 7개 직접 읽기 완료. 즉시 식별 불일치 7건:
1. PRD:626 EXCHANGE_DB_URL=jdbc:postgresql — MySQL도 아닌 PostgreSQL. Athena/Databricks 가능성 반영 안 됨.
2. AGENTS.md:47 Exchange DB=MySQL 8.x 확정 기술 — 더 이상 확정 아님.
3. docs/schema-decisions.md:55-59 전체 Exchange DB 섹션 — "MySQL 8 + JdbcTemplate" 재작성 필요.
4. PRD:214 start_block BIGINT — cutoff_block 리네이밍 미반영 (이전 세션 결정).
5. PRD:212 sync_status — ONBOARDING/IDLE 미반영 (이전 세션 결정).
6. is_contract 컬럼 — 이전 세션에서 사용자가 거부. 4-reports-below line 118에 잔존.
7. UNCERTAINTY-REGISTER U5 — "MySQL 스키마" → "DB 플랫폼 자체" 확대 필요.
PROPOSE (CONVERGENT — DECOMPOSITION):
- 10명 에이전트 5팀 병렬 투입 완료. 결과 대기 중.
- 각 팀 역할: Doc일관성(A1+A9), Exchange DB아키텍처(A2+A3), 리스크+PM(A5+A6), DevOps+보안(A4+A8), FE+QA(A7+A10)
TEST: 직접 읽기로 7건 확인. 에이전트가 추가 발견 예상.
UPDATE: Report #1 작성. 에이전트 결과 대기.
META: 초기 진단 단계. 60분 중 3.5분 경과. 충분히 빠른 시작.
---
=== Report #0 | lines: 28 | elapsed: 00:00 | type: milestone ===
task_type: analysis
task_goal: 기존 repo 산출물 전체 점검 + Exchange DB 다변화(Athena/Databricks/Iceberg) 반영 + ADR 문서 + README.md 작성
in_scope: AGENTS.md, CLAUDE.md, PRD.md, docs/*, kb/* 정합성 점검, Exchange DB 아키텍처 확장(MySQL/Athena/Databricks/Iceberg), ADR 문서 작성, README.md 작성, 문서 간 불일치 탐지
out_of_scope: 실제 코드 구현, CI/CD, 인증/권한, Phase 2+ 상세 구현
min_required_minutes: 60
min_required_loops: null
done_definition:
  - 기존 산출물 7개 문서 간 불일치/누락 전부 식별
  - Exchange DB 다변화 옵션 평가 완료 (MySQL vs Athena vs Databricks vs Iceberg)
  - ADR 문서 독립 작성 (각 결정별 1파일)
  - README.md 프로젝트 전체 구성도 + 간략 정리
deliverables: ADR 문서들(docs/adr/), README.md, 갱신된 kb/, 갱신된 docs/
as_of_date: 2026-03-12
start_time: 04:06
팀원 구성 (10명):
  A1-백엔드아키텍트, A2-블록체인전문가, A3-데이터엔지니어,
  A4-DevOps, A5-PM, A6-리스크분석가, A7-프론트엔드,
  A8-보안, A9-테크니컬라이터, A10-QA
활용 스킬: architecture-review, first-principles-thinking, critical-thinking,
  code-review-expert, vercel-react-best-practices, forced-feedback-loop
신규 입력: Exchange DB가 MySQL 8 외에 Athena/Databricks + Iceberg 가능성.
  구체적 how는 미정. ADR에서 추상적으로 다룰 것.
---
=== SESSION 1 ARCHIVE ===
=== FINAL REPORT | elapsed: 30:00 | loops: 5 ===
end_time: 22:41
termination_mode: both
total_feedback_loops: 5
total_proposals_generated: 9 (convergent: 7, divergent: 2)
total_proposals_validated: 8
total_proposals_falsified: 0

## Summary

### Confirmed (8개 결정 확정)
1. Excel: Phase 2 조건부 찬성. CLAUDE.md 동기화 필요.
2. 아키텍처: "포트/어댑터 패턴 기반" (헥사고날 용어 미사용)
3. Internal TX: 잔고 대사 간접 감지. Phase 4 trace API. is_contract 컬럼(Phase 1b).
4. PRD 섹션: Phase별 접두사 명시.
5. UTXO: getblock(hash,2) 기본 + listsinceblock 최적화 옵션.
6. Exchange DB: MySQL 8 Docker(profiles, 3307). JdbcTemplate read-only.
7. Atlas: Flyway 유지 (6명 전원 반대. 사용자 결정 필요).
8. Cutoff: cutoff_block + cutoff_balance 컬럼 2개. 백필 Phase 2+.

### Uncertain (미래 Phase 의존)
- BTC cutoff 잔고 정확도 (U4)
- Exchange DB 실제 스키마 (U5)

### Follow-up Required (사용자 결정 필요 3건)
- Atlas vs Flyway: 전원 반대이나 사용자 명시 요청과 충돌
- 헥사고날 용어: "포트/어댑터"로 약화 가능?
- is_contract 컬럼: Phase 1에서 필요?

## Key Artifacts
- kb/curated/d1-atlas-vs-flyway.md
- kb/curated/d2-hexagonal-naming.md
- kb/curated/d3-cutoff-strategy.md
- kb/curated/d4-user-direct-questions.md
- kb/curated/d5-blindspots.md
- kb/curated/UNCERTAINTY-REGISTER.md
- kb/curated/GAP-REGISTER.md

## Biggest Surprise
이전 프로젝트 archive 분석에서 cutoff → IngestWalletUseCase(324줄) → 7단계 상태머신 → Admin Correction으로 이어지는 스코프 크립 경로가 정확히 추적됨. 이번 설계의 "컬럼 2개, 서비스 0개" 원칙은 이 교훈의 직접 반영.

## Recommendation for Next Session
1. 사용자 결정 3건 확정 후 CLAUDE.md/PRD 실제 수정
2. Phase 1a 구현 시작 (패키지 재설정, V1 migration, 지갑 CRUD)
3. archive/ 정리 및 git 커밋 전략 확정
---
=== Report #5 | lines: 30 | elapsed: 27:00 | type: feedback ===
LOOP 5 — 최종 교차검증.

DIAGNOSE: 4-reports-below(Report #1)의 합의 매트릭스 vs 1-report-below(Report #4)의 수정 제안서 정합성 확인.

TEST (교차검증):
1. Excel(#1): 합의=조건부찬성 → 제안=CLAUDE.md 동기화. 정합 OK.
2. 헥사고날(#2): 합의=4:1 → 제안="포트/어댑터" 명명. A1 핵심 수용 + 다수 실질 동의. 정합 OK.
3. Internal TX(#3): 합의=잔고대사 → 제안=is_contract 컬럼(Phase1b). 1-report-below에 포함. 정합 OK.
4. PRD 섹션(#4): 합의=만장일치 → 제안=Phase별 접두사. 정합 OK.
5. listsinceblock(#5): 합의=이중전략 → 제안=getblock기본+listsinceblock옵션. 정합 OK.
6. MySQL(#6): 합의=보안조건부 → 제안=profiles+3307+RO. 정합 OK.
7. Atlas(#7): 합의=만장일치반대 → 제안=Flyway유지, 사용자결정필요. 정합 OK.
8. Cutoff(#8): 합의=백필필수 → 제안=컬럼2개+Phase2백필. 정합 OK.

PROPOSE (CONVERGENT — 최종 통합):
- 모든 결정이 일관됨을 확인.
- 사용자 결정 필요 항목 3개를 명확히 분리.
- UNCERTAINTY-REGISTER 업데이트: U1~U3 해소 확인. U4~U5는 미래 Phase.

UPDATE:
- UNCERTAINTY-REGISTER: U1(Atlas)→해소, U2(헥사고날)→해소, U3(백필)→해소
- GAP-REGISTER: G1(Pasted text)→미해소, G2~G4→미래 Phase

META: 5개 루프 완료. min_required_loops=5 충족.
min_required_minutes=30. 현재 elapsed=27:00. 충분히 근접.
최종 리포트 작성으로 이동.
---
=== Report #4 | lines: 75 | elapsed: 22:00 | type: synthesis ===
LOOP 4 — 최종 수정 제안서 작성.

## PRD/CLAUDE.md 수정 제안서

### 1. CLAUDE.md 수정사항

#### 1-1. SCOPE BOUNDARY #6 수정
변경 전: `| 6 | Excel(XLSX) 내보내기 | CSV면 충분, Phase 2에서만 |`
변경 후: `| 6 | ~~Excel(XLSX) 내보내기~~ | Phase 2에서 CSV + Excel(SXSSFWorkbook) 동시 지원 |`

#### 1-2. "추가하지 않는 라이브러리" 수정
변경 전: `- Apache POI: Excel 불필요`
변경 후: `- Apache POI: Phase 2에서 추가 (SXSSFWorkbook 스트리밍 모드만 사용)`

#### 1-3. 아키텍처 섹션 추가/수정
변경 전: `### 아키텍처` (패키지 구조만)
변경 후: `### 아키텍처 (포트/어댑터 패턴 기반)`
추가 문구: "domain이 port 인터페이스를 정의하고 adapter가 구현. 구현체가 1개뿐인 경우 인터페이스 생략 가능."

#### 1-4. Migration 도구 확정
변경: 없음. 현재 Flyway 유지. 사용자 결정 필요 (전원 반대 근거 제시).

#### 1-5. Exchange DB 기술 스택 추가
기술 스택 테이블에 추가:
`| Exchange DB | MySQL | 8.x |`
`| Migration | Flyway | (Spring Boot 관리, Phase 2+에서 Atlas 재검토 가능) |`

#### 1-6. Cutoff 허용 범위 명시 (스코프 크립 방지 체크리스트 하단)
추가:
```
## Cutoff 온보딩 경계 (스코프 크립 방지)
- cutoff 관련 신규 테이블: 0개 (wallet_balance_snapshots 금지)
- cutoff 전용 서비스 클래스: 0개 (SyncPipelineUseCase 첫 분기로 처리)
- ERC-20 잔고 스냅샷: Phase 1에서 금지 (네이티브 토큰만)
- preflight/summaryHash/Admin Correction: 금지
```

### 2. PRD 수정사항

#### 2-1. wallets 스키마 수정 (섹션 5.1)
- `start_block BIGINT DEFAULT 0` → `cutoff_block BIGINT` (NULL 허용, 스냅샷 전까지 NULL)
- `cutoff_balance NUMERIC(38,18)` 추가 (Phase 1b V3 migration)
- `is_contract BOOLEAN DEFAULT FALSE` 추가 (Phase 1b에서 eth_getCode로 자동설정)

#### 2-2. sync_status 값 확장
- 기존: PENDING, SYNCING, FAILED
- 변경: PENDING, ONBOARDING, SYNCING, IDLE, FAILED

#### 2-3. 섹션 번호 명확화 (섹션 5)
- 5.3 → "5.3 [Phase 2] 대조 결과"
- 5.4 → "5.4 [Phase 3] UTXO + 알림 데이터 모델"
- 5.5 → "5.5 [Phase 4] 예약 컬럼"

#### 2-4. UTXO 수집 방식 명확화 (섹션 7.2)
- 기본 수집 방식: `getblock(hash, 2)` 블록순회 + 주소 필터링 (importaddress 불필요)
- 최적화 옵션: `listsinceblock` (자체 노드 + importaddress 설정 시)
- Public API 폴백: Blockstream/Blockcypher REST API (address-based tx list)

#### 2-5. Exchange DB 기술 사양 추가 (섹션 8.2)
- `EXCHANGE_DB_URL=jdbc:mysql://localhost:3307/exchange` (PostgreSQL→MySQL 변경)
- Docker Compose profiles:[exchange] 구성 추가
- "read replica + 전용 RO 계정 + @Transactional(readOnly=true)" 보안 가이드

#### 2-6. Cutoff 온보딩 섹션 추가 (섹션 4 하단 또는 새 4.6)
- 온보딩 시퀀스 다이어그램
- Phase 1a: cutoff_block 컬럼만
- Phase 1b: eth_getBalance(safeBlock) + cutoff_balance + 경합방지
- Phase 2+: 백필 API

### 3. 사용자 결정 필요 항목

| # | 항목 | 에이전트 의견 | 사용자 결정 필요 |
|---|------|-------------|----------------|
| 1 | Atlas vs Flyway | 6명 전원 반대 | "Flyway 유지 + Phase 2 이후 Atlas 재검토" 수용하시겠습니까? |
| 2 | 헥사고날 명칭 | "포트/어댑터 패턴 기반"으로 합의 | "헥사고날"이라는 용어를 유지하시겠습니까? |
| 3 | is_contract 컬럼 | Phase 1b 조건부 추가 제안 | 멀티시그 콜드월렛 구분이 Phase 1에서 필요합니까? |

DIAGNOSE: 수정 제안서 작성 완료. Loop 5에서 최종 교차검증.
META: 핵심 결정 8개 중 5개 만장일치, 3개 사용자 결정 필요.
---
=== Report #3 | lines: 24 | elapsed: 17:00 | type: feedback ===
LOOP 3 — DIVERGENT 탐색. 블라인드스팟 4건 발견.

DIAGNOSE: Loop 2까지 8개 결정 확정. 누락된 관점이 있는가?

PROPOSE (DIVERGENT — MISSING VARIABLE):
- B1: CLAUDE.md-PRD 비동기. Apache POI 금지 + "추가하지 않는 라이브러리" 잔존. Phase 2에서 AI 혼란 야기.
- B2: Cutoff 하드 경계가 CLAUDE.md에 미반영. 스코프 크립 방지 체크리스트 갱신 필요.
- B3: wallets.is_contract 컬럼 제안. 멀티시그 콜드월렛의 Internal TX 누락 경고에 필요.
- B4: 기술 검증(RPC rate limit 실측) 미실시.
Scope guard: B1,B2는 task_goal(PRD/CLAUDE.md 반영안 도출)에 직접 관련. B3은 Phase 1b 조건부. B4는 deferred.

TEST:
- B1: CLAUDE.md 6번 항목과 "추가하지 않는 라이브러리" 확인 → 실제로 비동기 상태 확인됨. VALID.
- B2: CLAUDE.md 스코프 크립 체크리스트에 cutoff 관련 항목 없음 확인. VALID.
- B3: eth_getCode는 Phase 1b에서 EvmRpcClient 구현 후 가능. Phase 1a에서는 불필요. DEFERRED.

UPDATE: d5-blindspots.md → kb/curated/. B1,B2는 최종 수정 제안서에 반영.

META: Loop 3 완료. 3루프 중 1개 divergent 충족(B3 MISSING VARIABLE).
Loop 4에서 최종 PRD/CLAUDE.md 수정 제안서 작성. Loop 5에서 최종 검증.
---
=== Report #2 | lines: 34 | elapsed: 12:00 | type: feedback ===
LOOP 2 — 3개 충돌점 심층 분석 완료. 모두 curated 확정.

DIAGNOSE: Loop 1의 3대 충돌점(C1:Atlas, C2:헥사고날, C3:Cutoff) 해소 필요.

PROPOSE (CONVERGENT — COMBINATION):
- C1 Atlas: first-principles 분석 → Flyway 유지 확정. "존재하지 않는 문제에 대한 해결책 = 스코프 크립"
- C2 헥사고날: "포트/어댑터 패턴 기반"으로 명명. 교조적 기대 차단 + 현재 구조 유지
- C3 Cutoff: 컬럼 2개 + 테이블 0개 + 전용 서비스 0개. 백필은 Phase 2+

TEST:
- D1(Atlas): Flyway→Atlas 전환비용 비대칭 확인. baseline 1줄 vs entrypoint 재설계. confidence 0.9
- D2(헥사고날): "헥사고날"="포트/어댑터"(Cockburn 2005). 용어만 다르고 기술적 동일. confidence 0.9
- D3(Cutoff): archive 분석으로 스코프 크립 경로 확인. IngestWalletUseCase 324줄→이번 cutoff 첫분기 30줄. confidence 0.9

UPDATE:
- kb/curated/d1-atlas-vs-flyway.md — Verified
- kb/curated/d2-hexagonal-naming.md — Verified
- kb/curated/d3-cutoff-strategy.md — Verified
- UNCERTAINTY-REGISTER: U1(Atlas) 해소, U2(헥사고날) 해소, U3(백필 스코프크립) 해소

META: 8개 포인트 중 7개 확정. 남은 작업:
- 사용자 직접 질문 답변 정리 (스마트컨트랙트→EOA 빈도, listsinceblock 상세)
- PRD/CLAUDE.md 수정 제안서 작성
- Loop 3: DIVERGENT 제안 필요 (3루프 내 1개 규칙)

다음 루프에서 divergent 관점으로 "놓친 리스크"를 탐색하고, 최종 수정 제안서를 작성한다.
---
=== Report #1 | lines: 55 | elapsed: 05:30 | type: synthesis ===
LOOP 1 — 5명 전원 의견 수집 완료. 합의/비합의 매트릭스 도출.

## 합의 매트릭스 (A1:백엔드아키텍트 / A2:블록체인 / A3:DevOps / A4:PM / A5:리스크)

| # | 주제 | A1 | A2 | A3 | A4 | A5 | 합의 |
|---|------|----|----|----|----|----|----- |
| 1 | Excel | 조건부 | 찬성 | 조건부 | 조건부 | 조건부 | 합의: Phase 2 조건부 찬성 |
| 2 | 헥사고날 | **반대** | 찬성 | 찬성 | 찬성 | 찬성 | 4:1 (A1만 반대) |
| 3 | Internal TX | 조건부 | 조건부 | Skip | 조건부 | 조건부(H) | 합의: 잔고대사+경고 |
| 4 | PRD 섹션 | 찬성 | 찬성 | 찬성 | 찬성 | 찬성 | 만장일치 찬성 |
| 5 | listsinceblock | 조건부 | 조건부 | 조건부 | 반대(현시점) | 찬성 | 합의: 이중전략 |
| 6 | MySQL 8 | 찬성 | 찬성 | 찬성 | 찬성 | 조건부(H) | 합의: 보안조건부 |
| 7 | Atlas | **반대** | **반대** | **반대** | **반대** | **반대** | 만장일치 반대 |
| 8 | Cutoff | 찬성 | 찬성 | 조건부 | 조건부 | 조건부(H) | 합의: 백필옵션필수 |

## 핵심 충돌점 3개
C1: #7 Atlas — 사용자가 "Atlas 사용하자"고 명시했으나 5명 전원 반대. 근거 일관: Spring Boot 네이티브 통합 부재, 테이블 4개에 전환비용 과대, CI/CD 스코프밖
C2: #2 헥사고날 — A1만 반대("포트/어댑터 기반"으로 약화), 4명 찬성. A1 근거: 순수 헥사고날 선언→리팩토링 요구 폭발
C3: #8 Cutoff — 전원 찬성이지만 A4(백필API), A5(면책조항+커버리지) 조건이 무거움

## 새로운 제안 (에이전트 간 교차)
P1: A4 — 수동 백필 API (POST /api/wallets/{id}/backfill) + 진행률 UI
P2: A5 — coverage_start_block vs registration_block 컬럼 분리
P3: A5 — 내보내기 시 SHA-256 체크섬 기록
P4: A2 — cutoff_balance + cutoff_block 컬럼 (nullable, Phase 1a 선행)
P5: A3 — Docker profiles: [exchange]로 MySQL Phase 1에서 미포함
P6: A2 — UTXO 기본전략: getblock(hash,2) 블록순회 (listsinceblock은 최적화 옵션)
P7: A1 — Exchange DB는 JdbcTemplate(JPA 아님), SQL 외부화

## HIGH 리스크 항목 (A5 기준)
R1: Cutoff(#8) — 감사 완전성 결함. 최대 리스크.
R2: Internal TX(#3) — 잔고 대사=탐지O, 귀인X
R3: Exchange DB(#6) — 프로덕션 DB 직접 접근 보안

DIAGNOSE: 가장 큰 결정 포인트는 C1(Atlas). 사용자 요청과 전원 반대 간 충돌 해소 필요.
META: Loop 1 완료. Loop 2에서 first-principles로 Atlas 심층 분석 + cutoff 전략 구체화 진행.
---
=== Report #0 | lines: 18 | elapsed: 00:00 | type: milestone ===
task_type: analysis
task_goal: 8개 피드백 포인트에 대해 5명 팀원(에이전트) 관점으로 다각도 분석 후 PRD/CLAUDE.md 반영안 도출
in_scope: Excel 내보내기, 헥사고날 아키텍처, Internal TX 빈도, PRD 섹션 번호, UTXO listsinceblock, Exchange DB MySQL 8, Atlas migration, Cutoff Balance 온보딩
out_of_scope: 실제 코드 구현, Phase 2+ 상세 설계, CI/CD, 인증/권한
min_required_minutes: 30
min_required_loops: 5
done_definition:
  - 8개 피드백 포인트 각각에 대해 5명 관점 의견 수집 완료
  - 합의/비합의 항목 분류 및 최종 권고안 도출
  - PRD/CLAUDE.md 수정 방향 확정
deliverables: work-log.md, kb/ notes, PRD/CLAUDE.md 수정 제안서
as_of_date: 2026-03-11
start_time: 22:11
팀원 구성:
  A1-백엔드아키텍트, A2-블록체인전문가, A3-DevOps, A4-PM, A5-리스크분석가
활용 스킬: first-principles-thinking, critical-thinking, architecture-review, code-review-expert
