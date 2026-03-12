# Chain Adapter 전략

## EVM 체인 (ETH, POL, BSC)

### 수집 방식: 2-track 병렬 (ADR-005 참조)

- Track 1: `eth_getBlockByNumber(full=true)` → native transfer 필터
- Track 2: `eth_getLogs(Transfer topic)` → ERC-20 transfer 필터
- 합집합에 대해 `eth_getTransactionReceipt` 호출

### Cutoff 온보딩

- `eth_getBalance(address, safeBlock)` → 정확한 블록 시점 잔고
- safeBlock = latestBlock - confirmationBlocks

### 체인별 사양

| 항목 | ETH | POL | BSC |
|------|-----|-----|-----|
| Confirmation blocks | 12 | 128 | 15 |
| getLogs 범위 제한 | ~10,000 | ~3,500 | ~5,000 |
| Native symbol | ETH | POL | BNB |

### 알려진 한계

- Internal TX 미수집 (EC-1, `docs/known-edge-cases.md` 참조)
- Phase 4에서 trace API로 해소 예정

## UTXO 체인 (BTC, DOGE) — Phase 3

### 수집 방식: 이중 전략 (ADR-006 참조)

| 환경 | 방식 | 장점 | 단점 |
|------|------|------|------|
| 자체 노드 | `getblock(hash, 2)` 블록순회 + 주소 필터링 | importaddress 불필요, 범용적 | 전체 블록 스캔 필요 |
| 자체 노드 (최적화) | `listsinceblock` | 가장 효율적 | importaddress 필수, watch-only wallet 설정 필요 |
| Public API | Blockstream/Blockcypher REST | 인프라 불필요 | rate limit (10 req/s), DOGE 미지원(Blockstream) |

### Cutoff 온보딩

- BTC: 현재 잔고만 조회 가능, 과거 시점 불가 (EC-2 참조)
- Blockstream API: `chain_stats.funded_txo_sum - spent_txo_sum`

### `listsinceblock`은 public API에서 사용 불가

Bitcoin Core의 wallet RPC이다. 자체 풀노드 + `importaddress` 등록이 전제조건.
Blockstream, Blockcypher, Mempool.space 등 public API에서는 미제공.
