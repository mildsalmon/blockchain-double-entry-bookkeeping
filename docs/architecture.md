# Architecture: 포트/어댑터 패턴 기반

## 설계 원칙

- **포트/어댑터 패턴**: domain이 port 인터페이스를 정의하고, adapter가 구현한다.
- **구현체가 1개뿐이여도 인터페이스를 반드시 생성한다**: 다양한 저장소/외부 시스템 교체 대비.
- **SOLID 원칙 준수**:
  - SRP: 한 클래스는 하나의 책임. UseCase, Adapter, Controller 각각 분리.
  - OCP: 새 체인/저장소 추가 시 기존 코드 수정 없이 Adapter 구현체만 추가.
  - LSP: 모든 Adapter 구현체는 인터페이스 계약을 동일하게 이행.
  - ISP: 클라이언트가 사용하지 않는 메서드에 의존하지 않음. 필요 시 인터페이스 분리.
  - DIP: port가 추상, adapter가 구체. 의존 방향은 항상 adapter → domain.

## 패키지 구조

```
backend/src/main/kotlin/com/example/txcollector/
  adapter/
    inbound/           # REST Controller, Scheduler (외부 → 앱)
    outbound/          # JPA Repository, RPC Client, Exchange DB (앱 → 외부)
  application/
    port/
      input/           # UseCase 인터페이스 (인바운드 포트)
      output/          # Repository, ChainAdapter 인터페이스 (아웃바운드 포트)
    service/           # UseCase 구현체
    dto/               # Request/Response DTOs
  domain/
    service/           # 도메인 비즈니스 로직 (순수 Kotlin, 외부 의존 없음)
    model/             # data class, enum (Chain, Wallet, RawTransaction, SyncCursor)
  config/              # SchedulerConfig, DataSourceConfig
```

## 의존 방향

```
adapter/inbound → application/port/input → application/service → application/port/output ← adapter/outbound
                                            domain/service
                                            domain/model
```

- **domain**은 어떤 adapter에도 의존하지 않는다.
- **application/service**는 `application/port/output` 인터페이스만 사용한다.
- **adapter/inbound** (Controller, Scheduler)는 `application/port/input` 인터페이스를 호출한다.
- **adapter/outbound** (JPA, RPC Client 등)는 `application/port/output` 인터페이스를 구현한다.

## 다양한 Persistent Layer 지원

이 프로젝트는 저장소 계층이 확정되지 않은 상태에서 시작한다:

| 영역 | 후보 |
|------|------|
| 주 DB | PostgreSQL |
| 거래소 DB (Phase 2+) | Databricks, Athena, MySQL |
| TX 아카이브 (Phase 3+) | PostgreSQL, Parquet on S3 |

모든 저장소 접근은 `application/port/output` 인터페이스를 통해 추상화한다.
`@ConditionalOnProperty`로 어댑터 선택을 외부화하여, 설정 변경만으로 저장소를 교체할 수 있도록 한다.

## inbound vs outbound 구분

| 구분 | 역할 | 예시 |
|------|------|------|
| `adapter/inbound` | 외부 요청을 앱으로 전달 | REST Controller, @Scheduled, CLI |
| `adapter/outbound` | 앱이 외부 시스템에 접근 | JPA Repository, RPC Client, Exchange DB |
| `application/port/input` | inbound 어댑터가 호출하는 인터페이스 | SyncPipelineUseCase, SearchUseCase |
| `application/port/output` | outbound 어댑터가 구현하는 인터페이스 | WalletRepository, ChainAdapter |

## 체인 추가 시 변경 범위

새 체인(예: AVAX) 추가 시:
1. `adapter/outbound/AvaxAdapter.kt` 생성 (ChainAdapter 구현)
2. `chains` 테이블에 시드 데이터 추가
3. `.env`에 `CHAIN_AVAX_ENABLED`, `AVAX_RPC_URL` 추가
4. 기존 코드 변경 없음 (OCP)

## 관련 ADR

- [ADR-002: 포트/어댑터 패턴 채택](adr/ADR-002-port-adapter-pattern.md)
- [ADR-004: Exchange DB 접근 추상화](adr/ADR-004-exchange-db-abstraction.md)
