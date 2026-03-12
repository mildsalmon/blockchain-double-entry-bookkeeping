---
id: d5-blindspots
title: "D5: 블라인드스팟 분석 결과"
status: curated
confidence: 0.8
tags: [topic/risk, mode/divergent]
created: 2026-03-11
---

## 발견된 블라인드스팟

### B1: CLAUDE.md - PRD 비동기 (P0) — **해소됨**
- ~~CLAUDE.md "추가하지 않는 라이브러리"에 Apache POI 금지 잔존~~
- AGENTS.md line 25에 "Phase 2에서 허용: Excel(XLSX) 내보내기 (Apache POI SXSSFWorkbook)" 명시.
- CLAUDE.md는 AGENTS.md로 위임하므로 정합성 확보.

### B2: Cutoff 허용 범위 미명시 (P0) — **해소됨**
- ~~d3-cutoff-strategy의 "하드 경계"가 CLAUDE.md에 반영 안됨~~
- AGENTS.md line 139에 "cutoff 전용 서비스/테이블 금지" 명시.
- docs/schema-decisions.md "하드 경계" 섹션에 상세 기재.
- ADR-003에 독립 문서화 완료.

### B3: EOA vs Contract 구분 없음 (P1) — **사용자 거부됨**
- wallets.wallet_type이 HOT/COLD만. EOA/CONTRACT 미구분
- 멀티시그 콜드월렛 등록 시 Internal TX 누락 경고 표시 불가
- ~~제안: wallets.is_contract BOOLEAN 컬럼 + eth_getCode 자동판별~~
- **결정**: 사용자가 is_contract 컬럼 거부. 주소 유형 판별은 불필요. ADR-008 참조.
- 대안: 잔고 대사로 간접 감지. Internal TX 누락은 Phase 4 trace API로 해소.

### B4: 기술 검증 미실시 (P2)
- public RPC rate limit, getblock 응답 구조 등 문서 합의만 진행
- Phase 1b 착수 시 실측 필요

### 기각
- git 상태 정리: 사용자가 이미 archive/ 이동을 진행한 상태. 클린 리셋 의도에 부합.
