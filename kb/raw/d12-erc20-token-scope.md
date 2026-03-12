---
id: d12-erc20-token-scope
title: ERC-20 토큰 수집 범위 전략
status: raw
tags: [mode/divergent, topic/blockchain, topic/product]
created: 2026-03-12
confidence: 0.35
---

## 제안 (DIVERGENT — MISSING VARIABLE)

Phase 1b에서 ERC-20 Transfer 수집 시, 어떤 토큰을 대상으로 할지 미정의.

## 선택지

| 전략 | 장점 | 단점 |
|------|------|------|
| **모든 Transfer 이벤트 수집** | 누락 없음 | 스팸 토큰, 에어드랍 노이즈 대량 |
| **화이트리스트 토큰만 수집** | 깔끔한 데이터 | 누락 리스크, 관리 부담 |
| **모든 수집 + UI에서 필터** | 데이터 완전성 + 깔끔한 표시 | 저장 비용 증가 |

## PRD 현황

- R6에서 "알려진 토큰 화이트리스트 관리" 언급
- 하지만 화이트리스트 테이블/설정이 PRD 5.1 스키마에 없음
- Phase 1b-2 "ERC-20 Transfer 수집"의 범위 모호

## 제안: Phase 1b에서는 "모든 수집 + raw_data 보존"

- eth_getLogs(Transfer topic)로 모든 Transfer 이벤트 수집
- 스팸 토큰도 포함 (raw_transactions에 저장)
- 대시보드/API에서는 알려진 토큰 심볼로 필터 표시
- Phase 2 대조 시 Exchange DB에 있는 토큰만 대조 대상

## 관련성 검증

Phase 1b 수집 범위에 직접 영향. 구현 착수 전 결정 필요.
