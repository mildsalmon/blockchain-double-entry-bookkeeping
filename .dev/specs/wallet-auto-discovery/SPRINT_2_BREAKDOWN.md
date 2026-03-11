# Sprint 2 Breakdown

## Goal

omitted-at-cutoff token 이 발견됐을 때 허용되는 처리 경로를 고정하고, 운영자가 화면과 문서만으로도 같은 대응을 하게 만든다.

이번 스프린트의 완료 상태는 다음이다.
- correction policy 가 명시적으로 문서화된다.
- omitted token incident SOP 가 존재한다.
- close 전 / close 후 대응 원칙이 분리된다.
- 운영 UI 에 현재 정책이 노출된다.

## Document Set

- [CORRECTION_POLICY.md](/Users/mildsalmon/source_code/blockchain-double-entry-bookkeeping/.dev/specs/wallet-auto-discovery/CORRECTION_POLICY.md)
- [OMITTED_TOKEN_INCIDENT_RUNBOOK.md](/Users/mildsalmon/source_code/blockchain-double-entry-bookkeeping/.dev/specs/wallet-auto-discovery/OMITTED_TOKEN_INCIDENT_RUNBOOK.md)
- [APPROVAL_AUDIT_REQUIREMENTS.md](/Users/mildsalmon/source_code/blockchain-double-entry-bookkeeping/.dev/specs/wallet-auto-discovery/APPROVAL_AUDIT_REQUIREMENTS.md)

## Scope Boundary

이번 스프린트에 포함:
- correction policy 문서화
- omitted token incident runbook 작성
- approval / audit trail 요구사항 문서화
- operator UI 에 policy summary 반영

이번 스프린트에 포함하지 않음:
- in-place correction API
- admin-only correction tooling
- snapshot restate
- correction journal 생성
- token-level rescan / backfill

## Work Items

### Story 1: Correction Policy Baseline

목적:
- 팀이 omitted token 대응 경로를 동일하게 이해하게 만든다.

Tasks:
- `re-onboarding only` 기본 정책 명시
- 기존 wallet 의 in-place token 추가/수정은 허용하지 않음을 명시
- `FULL` 모드는 조사 용도이며 correction 자체가 아님을 명시

Acceptance Criteria:
- 정책 문서만 읽어도 허용/비허용 경계가 드러난다.

### Story 2: Omitted Token Incident Runbook

목적:
- 운영자가 omitted-suspected 신호를 봤을 때 행동 순서를 알게 만든다.

Tasks:
- triage 절차 정의
- close 전 / close 후 분기 정의
- evidence 수집 항목 정의

Acceptance Criteria:
- omitted incident 발생 시 SOP 를 따라 재현 가능하게 대응할 수 있다.

### Story 3: Approval And Audit Trail Requirements

목적:
- close 이후 발견 건이 임의 처리로 흐르지 않게 만든다.

Tasks:
- approval owner 정의
- 기록해야 할 최소 evidence 정의
- future admin correction flow 요구사항 정리

Acceptance Criteria:
- approval / audit trail 최소 요구사항이 문서로 고정된다.

### Story 4: Operator UI Policy Surface

목적:
- 운영자가 tool 안에서 현재 correction policy 를 바로 볼 수 있게 만든다.

Tasks:
- wallet status 에 correction policy 요약 표시
- omitted-suspected 항목이 있으면 action-required guidance 표시
- `FULL` fallback 의 역할을 correction path 와 구분해서 안내

Acceptance Criteria:
- 운영자가 omitted-suspected 를 봤을 때 다음 행동을 UI 에서 바로 알 수 있다.

## Suggested Order

1. Story 1
2. Story 2
3. Story 3
4. Story 4

## Testing

Frontend:
- correction policy UI render
- omitted-suspected 존재/부재 시 안내 문구 노출 확인

Regression:
- 기존 wallet list/status 렌더링 유지
- 기존 create / retry / delete flow 유지

## Ship Gate

- correction policy 문서 작성 완료
- incident runbook 작성 완료
- approval / audit trail 요구사항 문서 작성 완료
- operator UI 에 policy summary 반영 완료

## Completion Notes

Sprint 2 문서 산출물은 다음 판단을 고정한다.

- 기본 correction path 는 `re-onboarding only`
- close 이후 발견 건은 operator self-service correction 대상이 아님
- `FULL` 은 조사/감사 fallback 이지 correction 자체가 아님
- future admin correction flow 는 Sprint 3 후보로 분리
