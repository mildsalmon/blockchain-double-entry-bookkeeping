# Wallet Cutoff-Seeded Onboarding Sprint Plan

## Planning Assumptions

- 대상은 내부 운영 툴이다.
- 기본 온보딩 경로는 `BALANCE_FLOW_CUTOFF` 이다.
- 초기 보유 토큰은 운영자가 `trackedTokens` 로 seed 한다.
- cutoff 이후 새로 움직인 토큰은 tx/log activity 로 자동 발견한다.
- 병렬 최적화는 고려하지 않고, 순차적 스프린트 진행을 전제로 한다.

## Overall Goal

cutoff 기반 온보딩을 운영 가능하고 감사 가능한 프로세스로 만든다.

즉, 아래를 단계적으로 달성한다.
- 잘못된 기대를 줄인다.
- seed 입력 품질을 높인다.
- seeded / discovered / omitted-suspected 상태를 분리해서 보여준다.
- omitted token 사고가 나도 ad-hoc 처리로 흐르지 않게 한다.

## Sprint 1: Safe Cutoff Onboarding And Exception Visibility

### Goal

운영자가 cutoff wallet 을 등록할 때, 무엇이 snapshot 되는지 명확히 알고 안전하게 seed 하며, 등록 후 seeded / discovered / omitted-suspected 상태를 구분해 볼 수 있게 만든다.

### Scope

- cutoff-first UX 유지
- `trackedTokens` 입력 UX 개선
  - format validation
  - dedupe
  - helper text / examples
- seed preflight confirmation
  - 입력한 token address 를 symbol/name 으로 resolve
  - "이번 snapshot 은 ETH + seeded tokens only" 확인 단계 제공
- sign-off 절차 정의
- sign-off evidence 를 어떤 형태로 남길지 정의
- wallet-level seeded / discovered / omitted-suspected visibility 추가
- 최소 exception reporting 추가
- `FULL` fallback 사용 기준 문서화

### Deliverables

- wallet 등록 폼 개선안
- preflight confirmation UX
- sign-off checklist
- auditable sign-off record 설계
- seeded / discovered / omitted-suspected status surface
- exception reporting 규칙
- `FULL` fallback 운영 가이드

### Exit Criteria

- 운영자가 제출 전에 snapshot 범위를 확인할 수 있다.
- seed address 오입력/중복 입력을 기본적으로 차단한다.
- 운영자가 등록 후 seeded / discovered / omitted-suspected 상태를 구분해 볼 수 있다.
- 최소 exception reporting 기준이 정의된다.
- sign-off owner 와 evidence 저장 규칙이 정해진다.
- `FULL` 을 언제 쓰는지 운영 기준이 문서화된다.

### Risks

- address resolve 실패 시 preflight UX가 애매해질 수 있음
- sign-off를 문서만 만들고 실제 기록 저장을 미루면 효과가 약함

## Sprint 2: Correction Policy Operationalization

### Goal

omitted-at-cutoff token 이 발견됐을 때 허용되는 처리 경로를 고정하고, 운영자가 흔들리지 않도록 한다.

### Scope

- correction policy 확정
  - Phase 1 기본 정책: re-onboarding only
- omitted token incident SOP 구체화
- reporting period close 이후 발견 건 처리 규칙 정의
- correction 요청/승인 프로세스 정의
- 필요한 audit trail 추가

### Deliverables

- correction policy 문서
- omitted token incident runbook
- approval flow 정의
- audit trail 요구사항
- operator-facing policy summary

### Exit Criteria

- omitted token 사고가 나도 팀이 같은 방식으로 대응한다.
- re-onboarding only 정책이 실제 운영에서 수용 가능한지 확인된다.
- close 이후 발견 건에 대한 회계 처리 원칙이 정해진다.

### Risks

- 실제 운영에서 re-onboarding only가 너무 비싸면 곧바로 Phase 4 수요가 생김
- correction policy가 finance/accounting과 충돌할 수 있음

### Documentation Pack

- [CORRECTION_POLICY.md](/Users/mildsalmon/source_code/blockchain-double-entry-bookkeeping/.dev/specs/wallet-auto-discovery/CORRECTION_POLICY.md)
- [OMITTED_TOKEN_INCIDENT_RUNBOOK.md](/Users/mildsalmon/source_code/blockchain-double-entry-bookkeeping/.dev/specs/wallet-auto-discovery/OMITTED_TOKEN_INCIDENT_RUNBOOK.md)
- [APPROVAL_AUDIT_REQUIREMENTS.md](/Users/mildsalmon/source_code/blockchain-double-entry-bookkeeping/.dev/specs/wallet-auto-discovery/APPROVAL_AUDIT_REQUIREMENTS.md)
- UI policy surface in [SyncStatus.tsx](/Users/mildsalmon/source_code/blockchain-double-entry-bookkeeping/frontend/src/components/SyncStatus.tsx)

## Sprint 3: Optional Admin Correction Flow

### Goal

재등록 비용이 과도하다는 근거가 충분할 때만 admin-only correction path 를 도입한다.

### Scope

- admin-only missed cutoff token 추가 flow
- snapshot restate vs correction journal 중 하나 선택
- token-level backfill / rescan 지원 검토
- richer audit trail

### Deliverables

- correction flow spec
- accounting impact design
- admin UI/API 초안

### Exit Criteria

- correction flow 가 accounting consistency 를 깨지 않는 방식으로 정의된다.
- ad-hoc 수기 처리 대신 시스템화된 예외 경로가 생긴다.

### Risks

- 이 단계는 현재 구조상 가장 위험하다.
- `trackedTokens` immutability 와 synthetic opening entry 재처리 문제가 있어서 설계 난이도가 높다.

## Recommended Sequence

1. Sprint 1
2. Sprint 2
3. Sprint 3는 실제 운영 데이터가 쌓인 뒤 결정

## Suggested Release Cuts

- Release A:
  - Sprint 1 완료
  - 목표: 안전한 cutoff 등록 + 예외 가시화

- Release B:
  - Sprint 2 완료
  - 목표: correction policy operationalized

- Release C:
  - Sprint 3 완료 시
  - 목표: admin correction tooling

## What Not To Do Early

- 등록 시점 full auto-discovery 약속
- snapshot 이후 tracked token 즉시 수정 허용
- omitted token 을 ordinary discovery 로 자동 편입
- correction policy 없이 admin correction UI부터 만들기
