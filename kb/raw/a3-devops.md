---
id: a3-devops
title: DevOps/인프라 엔지니어 의견
status: raw
confidence: 0.8
tags: [topic/devops, topic/migration]
created: 2026-03-11
---

## 핵심 포지션

| # | 주제 | 입장 |
|---|------|------|
| 1 | Excel | 조건부 (SXSSFWorkbook 필수, heap -Xmx512m+, 동시요청 제한) |
| 2 | 헥사고날 | 찬성 |
| 3 | Internal TX | Skip |
| 4 | PRD 섹션 | 찬성 |
| 5 | listsinceblock | 조건부 (BTC 노드 Docker외부 운영, RPC URL .env 주입) |
| 6 | MySQL | **찬성** (profiles + 3307포트 + health check + ConditionalOnProperty) |
| 7 | Atlas | **반대** (Flyway 1줄 의존성 vs Atlas entrypoint wrapper 필요) |
| 8 | Cutoff | 조건부 (스냅샷 블록번호 기록 + sync_status 경합방지) |

## 주요 인사이트
- D1: MySQL docker-compose 상세 설계 - profiles: [exchange]로 Phase1에서 미포함, 3307포트, health check
- D2: Atlas 운영 복잡도 분석 - init container/entrypoint wrapper/sidecar 3가지 방안 모두 Flyway 대비 복잡
- D3: BTC 풀노드 디스크 650GB, DOGE 80GB - Docker Compose에 넣기 비현실적
- D4: Cutoff 경합방지 - wallets.sync_status='PENDING' 동안 스케줄러 제외
