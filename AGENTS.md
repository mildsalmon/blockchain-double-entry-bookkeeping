# AGENTS.md — AI Agent 공용 가이드라인

## 프로젝트 목적

거래소 핫/콜드 월렛의 멀티체인 온체인 트랜잭션을 5~10분 주기로 수집하고,
서비스 DB 기록과 대조하여 누락/불일치를 자동 탐지하는 내부 회계감사 도구.

---

## SCOPE BOUNDARY — 하지 않는 것

아래에 해당하는 코드, 스키마, UI를 **절대 생성하지 마라.**

| # | 금지 항목 | 이유 |
|---|----------|------|
| 1 | 복식부기 (journal, debit/credit) | 이전 프로젝트 과잉 복잡화의 원인 |
| 2 | FIFO / 원가 추적 (cost basis) | 세무/회계 도구 영역 |
| 3 | 가격 환산 (CoinGecko, KRW/USD) | 불필요 |
| 4 | 인증/권한 시스템 (Spring Security) | 블록체인 데이터는 수정 불가 |
| 5 | DEX 프로토콜 파싱 (Uniswap 분류기 등) | TX 수집에 불필요 |
| 6 | ERP/외부 시스템 연동 | 범위 밖 |
| 7 | 실시간 WebSocket 모니터링 | 5~10분 배치로 충분 |
| 8 | 멀티유저/다중 인스턴스 | 단일 배포 환경 |
| 9 | CI/CD 파이프라인 | 별도 요청 시만 |

**Phase 2에서 허용**: Excel(XLSX) 내보내기 (Apache POI SXSSFWorkbook)

---

## 현재 Phase: Phase 1a — 클린 리셋 + 멀티체인 스키마

Phase별 상세 목표는 `PRD.md` 섹션 6 참조.

### Phase 전환 기준
- **1a → 1b**: 4개 테이블 생성, 지갑 CRUD 동작, `docker compose up` 실행 가능
- **1b → 2**: EVM 3개 체인 TX 수집 동작, Etherscan 비교 누락 없음
- **2 → 3**: 대조 엔진 동작, 불일치 탐지 정상, CSV/Excel 내보내기 완료
- **3 → 4**: BTC/DOGE TX 수집/대조 동작, 알림 시스템 동작

---

## 기술 스택

| 계층 | 기술 | 버전 |
|------|------|------|
| Language | Kotlin | 2.1.x |
| Framework | Spring Boot | 3.4.x |
| Build | Gradle (Kotlin DSL) | 8.x |
| DB (주) | PostgreSQL | 16 |
| DB (거래소, Phase 2+) | Databricks 기본 (Athena 이식 예정, MySQL 폴백) | - |
| Migration | Flyway | (Spring Boot 관리) |
| Frontend | Next.js + React | 15.x / 18 |
| Styling | Tailwind CSS | 3.x |
| Container | Docker Compose | - |
| Test | JUnit 5, Testcontainers, MockWebServer | - |

---

## 아키텍처: 포트/어댑터 패턴 기반

SOLID 원칙을 준수한다. 상세는 `docs/architecture.md`, `docs/adr/ADR-002-port-adapter-pattern.md` 참조.

```
adapter/inbound → application/port/input → application/service → application/port/output ← adapter/outbound
                                            domain/service
                                            domain/model
```

- domain은 어떤 adapter에도 의존하지 않는다.
- 구현체가 1개뿐이여도 인터페이스를 반드시 생성한다 (다양한 저장소/외부 시스템 교체 대비).

---

## 코딩 컨벤션

### Kotlin
- data class는 domain/model에만
- Entity는 adapter/outbound에, 도메인 모델과 분리
- nullable 최소화
- 함수 파라미터 3개 초과 시 data class로 묶기

### Frontend
- 페이지: app/ (App Router), 컴포넌트: components/, API: lib/api.ts, 타입: types/

### 공통
- 함수/클래스명 영어, 주석 한국어 허용
- 매직 넘버 금지, 상수 추출
- **한 파일 300줄 초과 금지**
- TODO: `// TODO(phase-N): 내용`

## 작업 범위 제외 디렉토리

- `archive/`는 이전 구현 스냅샷이다.
- AI 에이전트는 `archive/`를 기본 검색/참조/수정 대상에서 제외한다.
- 사용자가 명시적으로 레거시 비교, 마이그레이션 참고, 과거 구현 확인을 요청한 경우에만 `archive/`를 연다.

---

## DB 스키마 변경 규칙

Flyway migration만 사용. 직접 DDL 금지.
상세 결정사항은 `docs/schema-decisions.md` 참조.

- 파일명: `V{N}__{snake_case_description}.sql`
- 기존 migration 수정 금지
- Phase별 V 번호: Phase 1(V1~V19), Phase 2(V20~V39), Phase 3(V40~V59), Phase 4(V60~V79)

---

## PR 규칙

1. **1 PR = 1 기능.** 여러 기능 섞지 않기.
2. PR 제목: `feat|fix|refactor|test(scope): 설명`
3. PR 본문에 반드시: 변경 이유, 테스트 방법, SCOPE BOUNDARY 자가 점검.
4. **한 PR에서 변경 파일 15개 초과 금지.**

---

## 스코프 크립 방지 체크리스트

**코드 생성 시 확인. 하나라도 "예"면 생성 중단 후 사용자에게 확인.**

```
[ ] 1. 현재 Phase 범위를 초과하는가?
[ ] 2. SCOPE BOUNDARY 금지 목록에 해당하는가?
[ ] 3. 새 외부 라이브러리를 추가하는가?
[ ] 4. 새 DB 테이블을 3개 이상 한 번에 추가하는가?
[ ] 5. "나중에 필요할 것 같아서" 미리 만드는 코드인가?
[ ] 6. 없어도 현재 Phase 목표를 달성할 수 있는가?
```

---

## "하지 말 것" (이전 프로젝트 교훈)

- **복식부기 모델 도입 금지.** 130개 파일로 비대해진 핵심 원인.
- **분류기(Classifier) 플러그인 구조 금지.**
- **가격 연동 금지.**
- **Admin Correction / preflight / summaryHash 금지.**
- **한 커밋에 10개 이상 파일 변경 금지.**
- **"하드닝" 명목의 무한 리팩토링 금지.** 기능이 동작하면 다음 Phase로.
- **cutoff 전용 서비스/테이블 금지.** SyncPipelineUseCase 분기로 처리.

---

## 상세 문서 참조

| 문서 | 내용 |
|------|------|
| `PRD.md` | 제품 요구사항, Phase별 기능 명세, 데이터 모델 |
| `docs/architecture.md` | 포트/어댑터 구조, SOLID 원칙, 패키지 규칙 |
| `docs/schema-decisions.md` | DB 설계 결정 (cutoff, sync_status, Exchange DB) |
| `docs/chain-adapters.md` | 체인별 수집 전략 (EVM, UTXO, public API) |
| `docs/known-edge-cases.md` | 알려진 엣지케이스 (Internal TX, UTXO 잔고 등) |
| `docs/adr/` | Architecture Decision Records (ADR-001~008) |
