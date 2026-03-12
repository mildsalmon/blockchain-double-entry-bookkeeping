# ADR-004: Exchange DB 접근 추상화

## 상태: 확정 (2026-03-12 갱신 — Databricks fact_crypto_transactions 반영)

## 맥락

거래소 서비스 DB와 대조(reconciliation)하기 위해 TX 데이터를 읽어야 한다.

**현재 상황**:
- 사용자가 Databricks에 `fact_crypto_transactions` 모델을 이미 구축해둔 상태
- 이를 Athena(S3 기반)로 이식하여 사용하는 것도 계획 중
- 경로: Databricks Delta → Parquet/Iceberg on S3 → Athena SQL

## 결정

**ExchangeTransactionPort 인터페이스로 추상화한다.**

Phase 2 기본 어댑터는 **Databricks**(기존 fact_crypto_transactions 활용)로 변경.
Athena 이식 완료 시 어댑터만 교체.

## 포트 인터페이스 (추상)

```
ExchangeTransactionPort (application/port/output/)
├── getTransactionsByChainAndPeriod(chainId, from, to) → List<ExchangeTransaction>
└── isHealthy() → Boolean

ExchangeTransaction (domain/model/)
├── txHash: String
├── chainId: String
├── amount: BigDecimal
├── timestamp: Instant
└── walletAddress: String
```

## 어댑터 구현 계획

| 어댑터 | 접근 방식 | 시점 |
|--------|----------|------|
| `DatabricksExchangeAdapter` | REST API (SQL Warehouse) 또는 JDBC Connector | Phase 2 (기본) |
| `AthenaExchangeAdapter` | AWS SDK + S3 (fact_crypto_transactions 이식 후) | Phase 2+ |
| `MySqlExchangeAdapter` | JDBC (폴백/대안) | 필요 시 |

## 플랫폼별 평가

| 기준 | Databricks | Athena | MySQL 8 |
|------|-----------|--------|---------|
| 쿼리 지연 | 1~30s (warm cluster) | 30s~5min (cold) | <100ms |
| 데이터 신선도 | 준실시간 | 배치(시간 단위) | 실시간 |
| 기존 모델 | **fact_crypto_transactions 존재** | 이식 예정 | 없음 |
| Spring Boot 연동 | REST API / JDBC Connector | AWS SDK | JDBC 네이티브 |
| 로컬 개발 | Community 계정 / Mock | LocalStack | Docker |

## fact_crypto_transactions → Athena 이식 경로

```
Databricks Delta Lake
  → Export to Parquet/Iceberg on S3
  → Register in AWS Glue Catalog
  → Athena SQL로 쿼리
```

어댑터 교체만으로 전환 가능 (`exchange.db.adapter=athena`).

## 어댑터 선택 방식

- `@ConditionalOnProperty("exchange.db.adapter")` 기반 어댑터 로딩
- Phase 2: `exchange.db.adapter=databricks` (기본)
- Athena 이식 완료 시: `exchange.db.adapter=athena`

## 로컬 개발

- Databricks Community Edition 또는 Mock 어댑터 사용
- 통합 테스트: MockWebServer로 Databricks REST API 모킹
- 실제 스키마: fact_crypto_transactions 기반

## 거부한 대안

- **JPA로 Exchange DB 매핑**: 외부 DB에 JPA 엔티티 강제는 과도
- **MySQL을 Phase 2 기본값으로**: 사용자가 이미 Databricks에 모델 구축함
- **Iceberg 직접 사용**: 테이블 포맷이지 DB가 아님, 별도 쿼리 엔진 필요

## 재검토 조건

- Athena 이식 완료 시 기본 어댑터 전환
- Databricks 접근 방식(REST vs JDBC) 확정 시 어댑터 구현 상세화
