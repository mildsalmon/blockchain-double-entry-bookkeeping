# ADR-008: Internal TX 처리 전략

## 상태: 확정 (Phase 4 이연)

## 맥락

EVM에서 스마트 컨트랙트가 다른 주소에 ETH를 직접 전송하는 "Internal TX"는
블록의 공식 TX 목록(`eth_getBlockByNumber`)에 나타나지 않는다.
거래소 운영에서 이 누락의 실제 영향을 평가하고 대응 전략을 결정해야 한다.

## 결정

**Phase 1~2에서는 잔고 대사로 간접 감지, Phase 4에서 trace API로 직접 수집.**

## 리스크 평가

| 시나리오 | 빈도 | 금액 규모 | 감지 |
|---------|------|----------|------|
| EOA→EOA 전송 | 매우 높음 | 다양 | 정상 감지 |
| Gnosis Safe 콜드→핫 보충 | 중간 | 높음 | **누락** |
| 스테이킹 보상 출금 | 낮음~중간 | 중간 | **누락** |
| DEX aggregator 정산 | 낮음 | 중간 | **누락** |
| 에어드랍 컨트랙트 | 매우 낮음 | 낮음 | **누락** |

거래소 입출금의 대부분은 EOA→EOA이므로 정상 감지된다.

## 간접 감지 (Phase 1~2)

- on-chain balance vs 장부 잔고 비교 → 차이 발생 시 알림
- 탐지: 가능 / 귀인(attribution): 불가 (어떤 TX가 차이를 유발했는지 특정 불가)
- UI에 "Internal TX 미수집" 경고 문구

## 직접 수집 (Phase 4)

- `debug_traceBlockByNumber` 또는 `trace_block`
- **전제조건**: 아카이브 노드 필요 (ETH 기준 12TB+ 디스크)
- 인프라팀과 노드 확보 사전 조율 필요

## 거부한 대안

- **Phase 1에서 즉시 trace API 도입**: 아카이브 노드 미확보, 과도한 초기 복잡성
- **is_contract 컬럼 추가**: 사용자가 거부. 주소 유형 판별은 불필요
- **Etherscan Internal TX API 사용**: Rate limit, 체인별 별도 API 키, 의존성 증가

## 향후 검토 사항 (미결정)

- **`eth_getCode` 등록 시점 체크**: 지갑 등록 시 EOA/컨트랙트 판별 + UI 경고 표시.
  `is_contract` 컬럼 추가와 별개로, 등록 시점 1회 체크만으로 저비용 안전장치 가능.
  핫월렛이 스마트 컨트랙트(Account Abstraction 등)인 경우 출금 TX 전체 누락 가능성 있음.
  → 구현 시점 미정. Phase 2 잔고 대사 결과에서 실제 영향 측정 후 결정.

## 재검토 조건

- Gnosis Safe 콜드월렛 사용 확인 시 Phase 4 선행 검토
- 잔고 대사에서 반복적 차이 발생 시 trace API 우선순위 상향
