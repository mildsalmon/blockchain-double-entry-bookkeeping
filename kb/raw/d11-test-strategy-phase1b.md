---
id: d11-test-strategy-phase1b
title: Phase 1b 테스트 전략 제안
status: raw
tags: [mode/divergent, topic/architecture]
created: 2026-03-12
confidence: 0.5
---

## 제안 (DIVERGENT — DECOMPOSITION)

PRD 1b-15 "통합 테스트"가 구체적 시나리오를 미명시. 핵심 테스트 시나리오 정의 필요.

## Phase 1b 테스트 시나리오 (우선순위순)

### 필수 (P0)

| # | 시나리오 | 도구 | 검증 |
|---|---------|------|------|
| T1 | EVM TX 수집 → DB 저장 | MockWebServer + Testcontainers(PG) | raw_transactions에 정상 저장 |
| T2 | 멱등성: 동일 TX 2회 수집 | 위와 동일 | ON CONFLICT DO NOTHING, 행 1개 |
| T3 | 증분 동기화: sync_cursors 업데이트 | 위와 동일 | cursor 이동 확인 |
| T4 | Flyway 마이그레이션 정상 | Testcontainers(PG) | 모든 V 파일 적용 |
| T5 | 지갑 CRUD API | MockMvc + Testcontainers | 200/400/404 응답 |

### 권장 (P1)

| # | 시나리오 | 검증 |
|---|---------|------|
| T6 | RPC 실패 시 재시도 + fail_count 증가 | MockWebServer 500 반환 |
| T7 | 동시 동기화 방지 (CAS) | 병렬 스레드 2개 → 1개만 성공 |
| T8 | sync_status 전이: PENDING→ONBOARDING→IDLE | 상태 전이 검증 |

## MockWebServer 사용 패턴

```kotlin
// RPC 응답 모킹
mockWebServer.enqueue(MockResponse()
  .setBody("""{"result": {"number": "0x12A05F200", "transactions": [...]}}""")
  .setHeader("Content-Type", "application/json"))
```

## 아카이브 참조

archive/에 20개 통합 테스트 존재. 유용한 패턴:
- IntegrationTestBase: Testcontainers 설정 공유
- MockWebServer 패턴: RPC 응답 모킹
- ConcurrencyTest: 동시성 검증

## 관련성 검증

Phase 1b 완료 기준에 "핵심 수집 로직 통합 테스트" 명시.
테스트 시나리오 미정의로 구현 시 범위 모호성 발생 가능.
