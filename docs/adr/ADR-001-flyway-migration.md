# ADR-001: Flyway를 DB Migration 도구로 선택

## 상태: 확정

## 맥락

DB 스키마 마이그레이션 도구로 Flyway와 Atlas(Ariga) 중 선택이 필요했다.
사용자가 Atlas를 제안했으나, 분석팀 전원(6명)이 Flyway를 권고했다.

## 결정

**Flyway를 사용한다.**

## 근거

- Spring Boot 네이티브 통합 (auto-configuration, zero-config)
- 테이블 4개 규모에서 선언적 마이그레이션의 이점이 미미
- Atlas는 CI/CD 파이프라인 의존도가 높은데, 현재 CI/CD는 스코프 밖
- 전환 비용 비대칭: Flyway→Atlas는 baseline 1줄로 가능, 역방향은 entrypoint 재설계 필요

## 거부한 대안

- **Atlas (Ariga)**: 선언적 스키마 관리 장점이 있으나, Spring Boot 통합 부재 + 현재 규모에서 과도

## Phase별 V 번호 대역

| Phase | V 번호 |
|-------|--------|
| Phase 1 | V1~V19 |
| Phase 2 | V20~V39 |
| Phase 3 | V40~V59 |
| Phase 4 | V60~V79 |

## 재검토 조건

- Phase 2 이후 테이블 20개 초과 시 Atlas 재검토 가능
