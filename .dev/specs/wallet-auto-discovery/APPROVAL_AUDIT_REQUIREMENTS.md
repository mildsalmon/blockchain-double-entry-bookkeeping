# Omitted Token Approval And Audit Trail Requirements

## Purpose

이 문서는 omitted-at-cutoff token incident 가 close 이후 발견됐을 때
- 누가 승인해야 하는지
- 어떤 evidence 가 남아야 하는지
- 현재 시스템이 어디까지 지원하는지
를 명확히 하기 위한 최소 요구사항이다.

현재 phase:
- operator correction tooling 없음
- in-place correction 허용 안 함
- 기본 정책은 `re-onboarding only`

## Approval Model

### Before Reporting Period Close

- 운영 실행 owner: ops reviewer
- 재등록 승인 owner: ops reviewer
- escalation 필요 조건:
  - omitted token 의 규모가 material 하다고 판단됨
  - seed omission 원인이 불명확함

### After Reporting Period Close

- 운영 실행 owner: 없음
- correction approval owner: finance / accounting approver
- engineering owner: system capability 및 변경 범위 평가

close 이후에는 operator 단독으로 아래 행위를 하면 안 된다.
- wallet 삭제 후 재등록
- omitted token 보정으로 해석될 수 있는 수동 조작
- `FULL` 결과를 근거로 cutoff ledger 가 자동 보정된 것으로 판단

## Minimum Evidence Record

incident 하나당 아래 필드는 반드시 기록한다.

- incident id 또는 추적 가능한 ticket id
- wallet address
- cutoff block
- original sign-off reviewer
- original sign-off reviewedAt
- original sign-off summary hash
- original seeded token list
- omitted token address
- omitted token symbol if known
- first seen block
- first seen at
- detection source
  - omitted-suspected UI
  - operator manual finding
  - reconciliation finding
- root cause hypothesis
- period status
  - pre-close
  - post-close
- chosen disposition
  - re-onboarding
  - escalate only
  - false positive

## Evidence Sources

허용 evidence 예시:
- wallet status screenshot
- preflight summary capture
- sign-off record snapshot
- block explorer evidence
- custody/export evidence
- operator note

## Current System Support

현재 시스템이 직접 제공하는 것:
- latest cutoff sign-off metadata
- seeded / discovered / omitted-suspected visibility
- preflight summary hash

현재 시스템이 아직 제공하지 않는 것:
- dedicated incident record table
- approval workflow engine
- post-close correction tooling
- correction journal / snapshot restate path

## Required Operating Procedure

1. incident 가 보이면 먼저 `OMITTED_TOKEN_INCIDENT_RUNBOOK.md` 를 따른다.
2. close 전이면 `CORRECTION_POLICY.md` 기준으로 re-onboarding 여부를 판단한다.
3. close 후면 operator 는 직접 수정하지 않고 finance / accounting approval 로 escalation 한다.
4. 모든 incident 는 최소 evidence record 를 남긴다.

## Future Systemization Targets

아래가 필요해지면 Sprint 3 범위로 올린다.

- dedicated incident ledger
- approver decision capture
- correction journal linkage
- admin-only correction flow
