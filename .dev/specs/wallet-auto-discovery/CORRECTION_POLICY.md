# Wallet Cutoff Correction Policy

## Policy Summary

현재 cutoff-first onboarding 정책에서 omitted-at-cutoff token correction 기본 원칙은 다음과 같다.

1. 기본 correction path 는 `re-onboarding only` 이다.
2. 기존 wallet 의 `trackedTokens`, `cutoffBlock`, opening balance 를 in-place 로 수정하지 않는다.
3. `FULL` 모드는 원인 조사와 evidence 수집용 fallback 이다.
4. `FULL` 실행 자체가 cutoff opening balance correction 을 의미하지 않는다.

## Allowed Actions

### Before Reporting Period Close

- omitted-suspected token 이 실제 omitted token 으로 확인되면 기존 wallet 을 삭제한다.
- complete seed list 로 동일 wallet 을 다시 등록한다.
- 새 cutoff onboarding 에 대해 다시 preflight 와 sign-off 를 수행한다.

### After Reporting Period Close

- operator 는 wallet 을 임의로 재등록하거나 in-place 수정하지 않는다.
- finance / accounting approval 없이는 correction action 을 시작하지 않는다.
- 현재 phase 에서는 system-level correction tooling 이 없으므로 운영 예외로 승격한다.

## Not Allowed

- 기존 cutoff wallet 에 token address 를 덧붙여 snapshot 을 “보정”하는 행위
- omitted token 을 ordinary discovered token 처럼 간주하고 opening balance correction 없이 무시하는 행위
- `FULL` mode 를 실행했다는 이유로 cutoff ledger 가 자동으로 보정됐다고 판단하는 행위

## Why This Policy Exists

- cutoff onboarding 은 synthetic opening balance 를 만든다.
- snapshot 이후 `trackedTokens` 를 수정하면 opening balance, sign-off evidence, 이후 delta ledger 가 서로 어긋난다.
- 현재 system 은 admin correction tooling 이 없으므로 무리하게 in-place correction 을 허용하면 auditability 가 깨진다.

## Required Evidence

close 전/후를 막론하고 omitted token incident 확인 시 최소한 아래 evidence 를 남긴다.

- wallet address
- cutoff block
- original seeded token list
- latest sign-off reviewer / reviewedAt
- omitted token contract address
- omitted token first-seen block / first-seen time
- omission root cause hypothesis

## Future Policy Transition Trigger

아래 조건이 반복되면 Sprint 3 admin correction flow 검토를 시작한다.

- re-onboarding cost 가 운영상 과도하다고 반복 보고됨
- close 이후 omitted incident 가 누적됨
- finance 팀이 correction journal or restate flow 를 요구함
