# ADR-007: 배치 스케줄링 전략

## 상태: 확정

## 맥락

5~10분 주기로 멀티체인 TX를 수집해야 한다.
스케줄링 도구로 Spring @Scheduled, Quartz, Spring Batch 등이 있다.

## 결정

**Spring @Scheduled를 사용한다.** Quartz/Spring Batch는 현재 규모에 과도하다.

## 실행 모델

- 중앙 스케줄러가 활성 체인별 동기화 작업을 디스패치
- **체인 간**: 병렬 (독립 스레드)
- **체인 내**: 순차 (같은 RPC에 과부하 방지)
- `sync_cursors` 테이블로 체인별 마지막 동기화 블록 추적

## 증분 동기화

```
매 배치:
  latestBlock = getLatestBlockNumber()
  safeBlock = latestBlock - confirmationBlocks  // reorg 안전 마진
  fromBlock = sync_cursors.last_synced_block + 1
  if fromBlock > safeBlock: skip
  else: fetchTransactions(fromBlock, safeBlock) → save → update cursor
```

## 실패 처리

- 실패 시 `fail_count` 증가, 다음 배치 주기에 자동 재시도
- 지수 백오프 (fail_count 기반)
- 연속 실패 임계값 초과 시 `sync_status = FAILED`, 수동 개입 필요

## 멱등성

- `ON CONFLICT DO NOTHING`으로 중복 TX 삽입 방지
- 커서 업데이트는 TX 저장 성공 후에만 수행

## 거부한 대안

- **Quartz**: 분산 스케줄링, 클러스터 지원 등 현재 불필요한 기능 과다
- **Spring Batch**: 대량 배치 처리 프레임워크, 5분 주기 경량 수집에 과도
- **외부 스케줄러 (cron/k8s CronJob)**: 앱 내부 상태(sync_cursors) 접근 필요

## 재검토 조건

- 체인 수가 10개 이상으로 증가하면 스레드풀 크기 및 병렬 전략 재검토
- 다중 인스턴스 배포 필요 시 분산 잠금 또는 Quartz 전환 검토
