---
id: d9-sync-traffic-light-definition
title: 동기화 신호등 상태 정의 제안
status: raw
tags: [mode/convergent, topic/product, topic/architecture]
created: 2026-03-12
confidence: 0.5
---

## 문제

PRD 시나리오 A와 Phase 1b-13에서 "체인별 초록/노랑/빨강" 신호등을 언급하지만,
각 색상의 조건이 정의되지 않았다.

## 제안: 신호등 조건 정의

| 색상 | 조건 | 의미 |
|------|------|------|
| 초록 (GREEN) | sync_status=IDLE AND last_sync < 10분 전 | 정상 동기화 중 |
| 노랑 (YELLOW) | sync_status=SYNCING OR last_sync 10~30분 지연 OR fail_count > 0 | 주의 필요 |
| 빨강 (RED) | sync_status=FAILED OR last_sync > 30분 지연 OR 체인 비활성 | 즉시 확인 필요 |

## 데이터 소스

- `sync_cursors.updated_at` → 마지막 성공 동기화 시각
- `wallets.sync_status` → 현재 상태
- `wallets.fail_count` → 실패 횟수
- `chains.is_active` → 체인 활성 여부

## 백엔드 API 필요

d6-scheduler-observability-gap에서 제안한 `GET /api/health/sync`가 이 데이터를 반환해야 함.
프론트엔드는 이 API 응답으로 신호등 색상을 결정.

## 관련성 검증

Phase 1b-13 구현 시 반드시 필요. PRD 시나리오 A의 핵심 워크플로우.
