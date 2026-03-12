# ADR-005: EVM 2-Track 수집 전략

## 상태: 확정

## 맥락

EVM 체인(ETH, POL, BSC)에서 온체인 TX를 수집할 때, 단일 RPC 호출로는
Native ETH 전송과 ERC-20 Transfer를 동시에 가져올 수 없다.

## 결정

**2-Track 병렬 수집을 채택한다.**

| Track | RPC 호출 | 수집 대상 |
|-------|---------|----------|
| Track 1 | `eth_getBlockByNumber(full=true)` | Native 전송 (ETH, MATIC, BNB) |
| Track 2 | `eth_getLogs(Transfer topic)` | ERC-20 Transfer 이벤트 |

## 근거

- `eth_getBlockByNumber`는 블록 내 모든 TX를 반환하지만 이벤트 로그는 미포함
- `eth_getLogs`는 특정 이벤트(Transfer)만 필터링 가능하지만 Native 전송은 미포함
- 두 호출을 조합해야 전체 TX를 놓치지 않음

## 멀티체인 코드 공유

- ETH/POL/BSC는 RPC URL과 confirmation blocks만 다름 (99% 코드 공유)
- `EvmChainAdapter(abstract)` → `EthereumAdapter`, `PolygonAdapter`, `BscAdapter`
- 체인별 설정은 `application.yml` 외부화

## Internal TX 한계

- 2-Track 수집으로도 Internal TX(Contract→EOA ETH 전송)는 감지 불가
- Phase 4에서 `trace_block` / `debug_traceBlockByNumber`로 해소 예정
- Phase 1~2에서는 잔고 대사로 간접 감지

## 거부한 대안

- **eth_getBlockByNumber만 사용**: ERC-20 Transfer 누락
- **Etherscan API 사용**: Rate limit 엄격, 체인별 별도 API, 자체 의존성 증가
- **WebSocket subscription**: 실시간 필요 없음 (5분 배치 충분)

## 재검토 조건

- Internal TX 수집이 급해지면 Phase 4 선행 검토
- 체인별 RPC 호환성 이슈 발생 시 어댑터별 분기 추가
