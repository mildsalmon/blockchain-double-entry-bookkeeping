---
id: d6-scheduler-observability-gap
title: 배치 스케줄러 관측성 갭
status: raw
tags: [mode/divergent, topic/devops, topic/risk]
created: 2026-03-12
confidence: 0.3
---

## 제안 (DIVERGENT — MISSING VARIABLE)

현재 설계에서 배치 스케줄러(@Scheduled)의 관측성이 부족하다.
스케줄러가 "정상 실행 중인지" 외부에서 확인할 방법이 없다.

## 갭 분석

| 질문 | 현재 답 |
|------|--------|
| 마지막 동기화 성공 시각은? | sync_cursors.updated_at으로 간접 추론 가능 |
| 스케줄러가 멈춰있는지 어떻게 아는가? | 방법 없음 |
| 체인별 동기화 지연(lag)은? | 계산 가능하지만 노출 API 없음 |
| 배치 실행 소요시간 추이는? | 기록 없음 |

## 최소 대응 (Phase 1b 범위 내)

1. `GET /api/health/sync` — 체인별 last_synced_block, lag, last_success_at 반환
2. 대시보드 "신호등"의 기준값을 sync_cursors에서 도출 (이미 PRD에 명시)
3. 구조화 로깅으로 배치 시작/종료/실패 기록

## Phase 2+ 확장 가능

- Micrometer + Prometheus 메트릭 (배치 소요시간, 실패율)
- Slack webhook (연속 실패 시)

## 관련성 검증

task_goal "기존 repo 산출물 전체 점검"과 직접 관련.
PRD 시나리오 A "일일 감사 체크"에서 대시보드 신호등이 이 데이터에 의존.
현재 PRD에 신호등 UI는 있으나 백엔드 health API가 명시되지 않음.
