---
name: PM/UX 미명세 항목
description: T3(PM/UX) 리뷰에서 발견된 사용자 워크플로우, UI 상태, 에러 처리 미명세 항목
type: project
---

## Phase 1b UI 구현 시 결정 필요

### CRITICAL
- **신호등 조건**: GREEN/YELLOW/RED 임계값 (lag 시간, fail_count 기반)
- **FAILED_TX UI**: 배지 표시, 상세 보기, gas fee 손실 리포트, "확인됨" 마킹
- **대조 결과 해석 가이드**: MatchStatus별 원인/조치/에스컬레이션 경로 테이블

### HIGH
- **CSV 포맷 명세**: 컬럼 이름, 순서, 날짜 형식, 인코딩(UTF-8 BOM), 요약 행 포함 여부
- **토큰 등록 UX**: 미등록 토큰 표시(greyed out), 등록 후 추가 가능 여부, symbol 자동완성
- **Sync status 가시성**: 체인/월렛별 상태 배지, 갱신 주기, SYNCING 30분 초과 시 자동 FAILED
- **대조 실행 워크플로우**: 동기/비동기, 진행률 표시, 최대 조회 기간(기본 90일?)

### MEDIUM
- **UI 빈 상태**: "지갑 없음" → 온보딩 화면, "TX 없음" → 안내 메시지, "불일치 0건" → 녹색 체크
- **에러 상태**: 일시적(재시도 중)/영구적(주소 오류) 구분, 사용자 메시지
- **TX 검색**: tx_hash 외 금액/날짜/주소 검색 지원 여부, 미발견 시 온체인 조회
- **잔고 표시**: Phase 1b 단순 표시(마지막 동기화 기준), Phase 4 과거 시점 조회
- **Rate limit 메시지**: "요청 과다 - X초 후 가능" 토스트
- **Phase 1b→2 DoD**: 테스트 커버리지 목표(70% critical paths), 실제 지갑 24시간 동기화 검증
