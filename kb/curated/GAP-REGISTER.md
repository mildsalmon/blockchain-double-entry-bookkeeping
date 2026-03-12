---
id: gap-register
title: Gap Register
status: curated
tags: [type/register]
created: 2026-03-11
---

| ID | Missing Data | Impact | Fallback | Confidence Penalty | Follow-up |
|----|-------------|--------|----------|-------------------|-----------|
| G1 | Pasted text #2 내용 미확인 | LOW | 사용자에게 재확인 요청 | -0.1 | 다음 세션 |
| G2 | Exchange DB 테이블 스키마 | MED | Docker MySQL 더미 스키마 | -0.15 | 사용자 제공 대기 |
| G3 | 재무팀 BTC 대조 시급성 | MED | Phase 3 유지(기본) | -0.1 | 사용자 확인 |
| ~~G4~~ | ~~회사 PC BTC 노드 보유 여부~~ | ~~해소~~ | BTC/DOGE 노드 있음 확인 | 0 | 해소 |
| G5 | 대조 오류 예산(허용 불일치 임계값) 미정의 | MED | 0 tolerance 기본값 | -0.1 | Phase 2 설계 시 재무팀 확인 |
| G6 | Cutoff stale 감지 메커니즘 미정의 | MED | cutoff_block != null 검사로 대체 | -0.1 | Phase 1b 구현 시 |
| G7 | PRD Phase 4 완료 기준 미명시 | LOW | 개별 수락 기준으로 대체 | -0.05 | Phase 4 진입 시 |
| G8 | Taskfile.yml 미생성 (`task verify` 정의 필요) | MED | archive/Taskfile.yml 참조하여 재생성 | -0.1 | Phase 1a 구현 시 |
| ~~G9~~ | ~~대시보드 신호등 조건 미정의~~ | ~~해소~~ | 사용자 승인: GREEN <10분, YELLOW 10~30분, RED >30분 | 0 | 해소 |
| ~~G10~~ | ~~콜드월렛 유형 (EOA vs 멀티시그) 미확인~~ | ~~해소~~ | EOA 확인됨 | 0 | 해소 |
