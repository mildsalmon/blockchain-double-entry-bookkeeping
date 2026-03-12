# ADR-002: 포트/어댑터 패턴 채택

## 상태: 확정 (2026-03-12 갱신)

## 맥락

아키텍처 패턴 명명과 범위를 결정해야 했다.
"헥사고날 아키텍처"라는 용어가 교조적 기대를 유발할 수 있다는 우려가 있었다.
또한 저장소 계층(PostgreSQL, Parquet, Databricks 등)과 외부 시스템(RPC, Exchange DB)이
다양하게 교체될 가능성이 높아, 인터페이스 추상화가 필수적이다.

## 결정

**"포트/어댑터 패턴 기반"으로 명명한다.** SOLID 원칙을 준수한다.

## 핵심 규칙

- domain은 어떤 adapter에도 의존하지 않는다
- application/service는 port 인터페이스만 사용한다
- **구현체가 1개뿐이여도 인터페이스를 반드시 생성한다** (다양한 저장소/외부 시스템 교체 대비)

## 다양한 Persistent Layer 지원

이 프로젝트는 저장소 계층이 확정되지 않은 상태에서 시작한다:

| 영역 | 후보 |
|------|------|
| 주 DB | PostgreSQL, File-based (Parquet) |
| 거래소 DB | Databricks, Athena, MySQL |
| TX 아카이브 | PostgreSQL, Parquet on S3 |

모든 저장소 접근은 Port 인터페이스를 통해 추상화한다.
`@ConditionalOnProperty`로 어댑터 선택을 외부화하여,
설정 변경만으로 저장소를 교체할 수 있도록 한다.

## 패키지 구조

```
adapter/
  inbound/           # REST Controller, Scheduler (외부→앱)
  outbound/          # JPA Repository, RPC Client, Exchange DB (앱→외부)
application/
  port/input/        # UseCase 인터페이스 (인바운드 포트)
  port/output/       # Repository, ChainAdapter 인터페이스 (아웃바운드 포트)
  service/           # UseCase 구현체
domain/
  service/           # 도메인 비즈니스 로직
  model/             # data class, enum
```

## SOLID 적용

| 원칙 | 적용 |
|------|------|
| SRP | Service, Adapter, Controller 각각 단일 책임 |
| OCP | 새 체인/저장소 추가 시 Adapter 구현체만 추가, 기존 코드 무수정 |
| LSP | 모든 Adapter 구현체는 인터페이스 계약 동일 이행 |
| ISP | 불필요한 메서드 의존 금지, 필요 시 인터페이스 분리 |
| DIP | port가 추상, adapter가 구체 |

## 의존 방향

```
adapter/inbound → application/port/input → application/service → application/port/output ← adapter/outbound
                                            domain/service
                                            domain/model
```

## 거부한 대안

- **"헥사고날 아키텍처" 명명**: 기술적으로 동일하나 교조적 해석 위험
- **레이어드 아키텍처**: domain→adapter 의존 방향이 역전되어 부적합
- **인터페이스 생략 허용**: 저장소 다양성 고려 시 모든 포트에 인터페이스 필수

## 재검토 조건

- 패키지 구조가 실제 구현 시 불편하면 세부 조정 가능
- 저장소 플랫폼 확정 시 불필요한 추상화 제거 검토
