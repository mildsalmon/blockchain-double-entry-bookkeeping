# Omitted Token Incident Runbook

## Trigger

아래 중 하나가 보이면 runbook 을 시작한다.

- wallet status 에 `omitted-suspected` candidate 가 표시됨
- operator 가 cutoff 시점 seed 누락 가능성을 수동으로 인지함
- reconciliation 과정에서 cutoff opening balance 와 실제 보유 토큰 목록 불일치가 발견됨

## Step 1: Confirm The Signal

1. wallet address 와 cutoff block 을 확인한다.
2. omitted candidate token address 를 확인한다.
3. `Discovered After Cutoff` 와 `Omitted-Suspected` first-seen block 을 비교한다.
4. 해당 token 이 cutoff 시점부터 실제 보유 중이었는지 operator evidence 를 수집한다.

## Step 2: Collect Evidence

기록 항목:
- wallet address
- cutoff block
- original sign-off reviewer / reviewedAt
- seeded token list
- omitted token address
- omitted token first-seen block / first-seen time
- 근거 자료
  - explorer screenshot
  - custody/export evidence
  - operator note

## Step 3: Determine Timing

### Case A: Reporting Period Not Closed

1. existing wallet 을 삭제한다.
2. complete seed list 로 wallet 을 다시 등록한다.
3. preflight 결과와 reviewer sign-off 를 다시 남긴다.
4. 새 wallet 상태에서 omitted signal 이 사라졌는지 확인한다.

### Case B: Reporting Period Already Closed

1. operator 는 wallet 을 직접 수정하지 않는다.
2. finance / accounting approver 에 incident 를 escalation 한다.
3. 현 시스템에는 in-place correction tooling 이 없음을 명시한다.
4. incident 를 운영 예외 backlog 로 남긴다.

## Step 4: Root Cause Classification

incident 를 아래 중 하나로 분류한다.

- seed omission
- wrong contract address
- operator misunderstanding of cutoff scope
- false positive from post-cutoff first activity

## Step 5: Follow-Up Actions

- seed omission / wrong address:
  - WalletInput 도움말과 checklist 개선 후보로 등록
- misunderstanding:
  - operator onboarding / runbook 강화
- false positive:
  - omitted-suspected heuristic 조정 backlog 로 등록

## Escalation Owner

- 운영 판단 owner: ops reviewer
- close 이후 correction approval owner: finance / accounting approver
- 시스템 변경 owner: engineering
