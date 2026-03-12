# Known Edge Cases & Future Issues

수집/대조 과정에서 알려진 엣지케이스를 기록한다.
문제가 발생하면 이 문서에 케이스를 추가하고, 해결되면 상태를 갱신한다.

---

## EC-1: 멀티시그 콜드월렛(Gnosis Safe)의 Internal TX 누락

**상태**: 미해결 (Phase 4에서 해소 예정)
**심각도**: HIGH
**영향 체인**: EVM (ETH, POL, BSC)

### 문제

거래소 콜드월렛이 Gnosis Safe(멀티시그 컨트랙트)인 경우,
콜드→핫 ETH 보충 TX가 Internal TX로 발생한다.

```
[Gnosis Safe 콜드월렛] --execTransaction()--> [핫월렛 EOA]
                              ↑
                         Internal TX
                         eth_getBlockByNumber에 미포함
                         eth_getLogs에 미포함
```

일반 블록 스캔(eth_getBlockByNumber + eth_getLogs)으로는 이 TX를 감지할 수 없다.

### 실제 발생 조건

| 시나리오 | 빈도 | 금액 규모 |
|---------|------|----------|
| Gnosis Safe → 핫월렛 ETH 보충 | 중간 (콜드→핫 이체 시마다) | 높음 (대규모) |
| 스테이킹 보상 출금 (Lido 등) | 낮음~중간 | 중간 |
| DEX aggregator 정산 | 낮음 | 중간 |
| 에어드랍 컨트랙트 분배 | 매우 낮음 | 낮음 |

**참고**: 거래소 입출금의 대부분은 EOA→EOA 전송이므로 정상 감지된다.
스마트 컨트랙트가 EOA에 직접 물량을 뿌리는 경우는 드물고,
대부분 컨트랙트→중간 EOA→최종 배포 구조를 사용한다.

### 현재 대응 (Phase 1~2)

1. **잔고 대사로 간접 감지**: on-chain balance vs 장부 잔고 비교 시 차이 발생으로 탐지
   - 탐지: 가능 (잔고 차이 감지)
   - 귀인(attribution): 불가능 (어떤 TX가 차이를 유발했는지 특정 불가)
2. **UI 경고**: 대시보드에 "Internal TX는 현재 수집 범위에 포함되지 않습니다" 안내

### 해결 방안 (Phase 4)

- `debug_traceBlockByNumber` 또는 `trace_block`으로 Internal TX 직접 수집
- **전제조건**: 아카이브 노드 필요 (ETH 기준 디스크 12TB+)
- 회사 인프라팀과 아카이브 노드 확보 사전 조율 필요

### 임시 워크어라운드

잔고 대사에서 차이 발생 시, Etherscan의 "Internal Transactions" 탭을 수동 조회하여 원인 파악 가능.

---

## EC-2: UTXO 체인 과거 시점 잔고 조회 불가

**상태**: 미해결 (Phase 3~4에서 해소 예정)
**심각도**: MED
**영향 체인**: BTC, DOGE

### 문제

EVM은 `eth_getBalance(address, blockNumber)`로 과거 시점 잔고를 정확히 조회할 수 있다.
BTC/DOGE는 이런 API가 없다. 현재 잔고만 조회 가능.

- 자체 노드: `listunspent` → 현재 미사용 UTXO 합계 = 현재 잔고
- Public API: Blockstream `chain_stats.funded_txo_sum - spent_txo_sum` = 현재 잔고

### 영향

- Cutoff 온보딩 시 BTC 잔고 스냅샷이 "현재 시점 근사값"
- 등록 시점과 잔고 조회 시점 사이에 TX가 들어오면 오차 발생 가능

### 대응

- Phase 1a~1b: EVM만 다루므로 영향 없음
- Phase 3: BTC cutoff_balance는 "등록 시점 근사값"으로 기록, UI에 명시
- **Phase 3 이후 잔고 역산 가능**: cutoff 시점 잔고 + 이후 수집된 TX(입금 vout - 출금 vin)로 cutoff 이후 시점의 잔고를 역산할 수 있다. 단, cutoff 이전 시점 역산은 불가.
- ADR-006 "UTXO 기반 잔고 역산" 섹션 참조

---

## EC-3: CSV 한글 인코딩 깨짐

**상태**: Phase 2에서 Excel 지원으로 해소 예정
**심각도**: LOW
**영향**: 재무팀 실무

### 문제

Windows Excel은 CSV를 기본적으로 EUC-KR/CP949로 연다.
UTF-8 CSV를 더블클릭하면 한글이 깨진다.

### 대응

- Phase 2: CSV(UTF-8 BOM 포함) + Excel(XLSX) 동시 제공
- Excel은 Apache POI SXSSFWorkbook(스트리밍 모드)으로 구현

---

## EC-4: 소수점 정밀도 손실 (EVM wei ↔ decimal 변환)

**상태**: 미해결 (Phase 1b 구현 시 검증 필요)
**심각도**: MED
**영향 체인**: EVM (ETH, POL, BSC)

### 문제

EVM TX의 value는 wei(uint256)로 표현된다. `NUMERIC(38,18)`로 변환 시
극단적 큰 값에서 오버플로우 가능성이 있다. uint256 최대값은 ~78자리.

### 대응

- `value_raw VARCHAR(78)`에 원본 보존 (정밀도 손실 방지)
- `value_decimal NUMERIC(38,18)`은 표시용 변환값
- 대조 시 `value_raw` 기준 비교 우선

---

## EC-5: Reorg 발생 시 이미 저장된 TX 처리

**상태**: 미해결 (Phase 1b 설계 시 결정)
**심각도**: HIGH
**영향 체인**: 전체 (EVM + UTXO)

### 문제

confirmation blocks 이내 블록이 reorg되면, 이미 DB에 저장된 TX가 무효화될 수 있다.
현재 설계는 `safeBlock = latestBlock - confirmationBlocks` 마진으로 방지하지만,
마진보다 긴 reorg(드물지만 가능)는 대응 불가.

### 대응

- `sync_cursors.last_block_hash`로 체인 연속성 검증
- hash 불일치 시 해당 블록 이후 TX 재수집 (soft reorg 처리)
- 깊은 reorg(>confirmation blocks): 감지 후 수동 개입 알림

---

## EC-6: RPC 엔드포인트 스로틀링/장애

**상태**: 미해결 (Phase 1b 구현 시 대응)
**심각도**: MED
**영향**: 전체 체인 수집

### 문제

Public RPC 엔드포인트는 rate limit이 있고, 일시적 장애가 발생할 수 있다.
자체 노드도 과부하 시 응답 지연/거부 가능.

### 대응

- `fail_count` 기반 지수 백오프
- 연속 실패 시 `sync_status = FAILED` 전환
- 체인 내 순차 실행으로 단일 RPC 과부하 방지
- 향후 서킷브레이커 패턴 검토 가능

---

## EC-7: Cutoff 스냅샷 Stale 위험

**상태**: 미해결 (Phase 1b 설계 시 결정)
**심각도**: LOW
**영향**: Cutoff 온보딩 정확도

### 문제

`eth_getBalance` 호출 시점과 `cutoff_block` 기록 시점 사이에 TX가 들어오면
cutoff_balance가 실제 해당 블록 잔고와 미세하게 다를 수 있다.

### 대응

- `eth_getBalance(address, cutoff_block)` — 블록 번호 지정으로 정확한 시점 조회
- UTXO 체인은 과거 시점 잔고 조회 불가 → EC-2 참조
- cutoff_balance는 "근사값"으로 UI에 명시
