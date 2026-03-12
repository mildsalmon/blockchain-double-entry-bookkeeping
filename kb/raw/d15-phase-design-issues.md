---
name: Phase별 미해결 설계 이슈
description: Session 3 Loop 1에서 발견된 Phase 설계 시 결정 필요한 이슈 목록
type: project
---

## Phase 1b 진입 전 결정 필요

- **F7**: RPC 비밀/시크릿 관리 전략 (T2+T5 교차검증)
  - .env 평문 RPC URL, API 키 노출 리스크
  - Spring Cloud Vault 또는 환경별 .env.local 분리

- **F8**: Reorg 롤백 전략 (T2+T5 교차검증)
  - sync_cursors.last_block_hash 컬럼 추가 여부
  - soft reorg (confirmation 내) vs deep reorg (confirmation 초과) 처리 분리
  - TX 재수집 시 DELETE vs MARK 전략

- **F9**: 신호등 조건 정의 (T5)
  - GREEN: sync_status=IDLE AND last_sync < 10분
  - YELLOW: sync_status=SYNCING OR lag 10~30분 OR fail_count > 0
  - RED: sync_status=FAILED OR lag > 30분

- **F10**: Cutoff onboarding 실패 복구 (T2+T5 교차검증)
  - eth_getBalance 실패 시: fail_count++ → 3회 초과 시 FAILED
  - ONBOARDING 30분 이상 지속 시 자동 PENDING 복원

- **F12**: 어댑터 하위 디렉토리 구조 (T1)
  - adapter/inbound/controller/, adapter/inbound/scheduler/
  - adapter/outbound/persistence/, adapter/outbound/chain/

- **F13**: 포트 네이밍 컨벤션 (T1)
  - Interface: [DomainConcept]Port 또는 [DomainConcept]Repository
  - Implementation: [Specific][DomainConcept]

- **F16**: 2-track 수집 원자성 (T2)
  - Track 1 성공 + Track 2 실패 시 전체 ROLLBACK vs 독립 저장

## Phase 3 진입 전 결정 필요

- **F14**: UTXO 거스름돈 감지 휴리스틱 (T2)
  - wallet 알려진 주소 화이트리스트로 자동 식별
  - 미식별 시 raw_data JSONB 분석으로만 특정

- **F15**: UTXO Public API rate limit 전략 (T2)
  - Blockstream 1 req/sec → 100 지갑 모니터링 불가
  - DOGE: 1분/블록 → 하루 1440블록 (BTC의 10배 순회 부하)
  - 자체 노드 필수 또는 유료 API 사용

- **F18**: `importaddress` 사전 준비 (인프라팀 조율)
  - Phase 2 중반에 인프라팀에 사전 요청
  - `rescan=false` 가능 여부 확인 (cutoff 방식이면 과거 TX 불필요)
  - DOGE는 descriptor wallet 미지원 → `importaddress` 방식만 가능
  - ADR-006에 상세 기록됨

## Phase 2+ 운영 준비

- **F17**: 백업/DR 전략 (T5)
  - PostgreSQL WAL 아카이빙 또는 pg_dump 일일 백업
  - RTO 4시간, RPO 24시간 제안
