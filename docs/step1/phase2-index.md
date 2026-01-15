# Phase 2: 인덱스 적용 (캐시 OFF + 인덱스 ON)

## 목적

- **인덱스만 추가**하여 DB 조회 성능 개선 효과 측정
- Phase 1과 비교하여 인덱스의 정량적 효과 확인
- `EXPLAIN ANALYZE`로 Index Scan 전환 확인

---

## 준비 작업

### 인덱스 생성

```sql
-- 인덱스 생성 (CONCURRENTLY로 운영 중에도 안전하게)
CREATE INDEX CONCURRENTLY idx_url_short_url ON url("shortUrl");

-- 인덱스 생성 확인
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'url' AND indexname = 'idx_url_short_url';
```

**실행 체크리스트**:

- [ ] 인덱스 생성 (`CREATE INDEX CONCURRENTLY`)
- [ ] 인덱스 생성 확인
- [ ] 캐시는 Phase 1과 동일하게 비활성화 상태 유지

---

## 모니터링

**Grafana 대시보드**: `http://localhost:3000` → "Step 1: Phase별 성능 비교" 선택

**Phase 변수 설정**: 대시보드 상단에서 **Phase 2** 선택

**확인 항목**:

- Phase 1과 동일한 메트릭 + Phase 1 대비 개선율 확인
- HTTP 응답 시간 개선 (p95, p99)
- DB 부하 변화

**상세 가이드**: [모니터링 가이드](./monitoring-guide.md)

---

## 검증 방법

### EXPLAIN ANALYZE 비교

```sql
-- 동일한 쿼리로 재실행 (Phase 1에서 사용한 동일한 shortCode 값)
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT * FROM url WHERE "shortUrl" = '동일한_shortCode_값';
```

**확인 포인트**: Plan 변경 (Seq Scan → Index Scan), Execution Time, Buffers, Rows

**실행 체크리스트**:

- [ ] Phase 1과 동일한 쿼리로 EXPLAIN ANALYZE 재실행
- [ ] Phase 1 결과와 비교
- [ ] Plan 변경 확인 (Seq Scan → Index Scan)
- [ ] 결과 저장 (Phase1-vs-Phase2-explain.txt)

---

## k6 실행

**Phase 1과 동일한 시나리오 사용**:

```bash
cd k6-tests
k6 run --out json=results/results-phase2.json scenario-phase1.js
```

또는 환경 변수로 Phase 지정:

```bash
cd k6-tests
PHASE=phase2 k6 run scenario-phase1.js
```

**참고**: Phase 1과 동일한 데이터셋(`step1-dataset.json`)과 동일한 시나리오를 사용하여 공정한 비교가 가능합니다.

**실행 체크리스트**:

- [ ] `k6-tests/test-data/step1-dataset.json` 파일 존재 확인
- [ ] Phase 1과 동일한 k6 시나리오 사용
- [ ] k6 테스트 실행
- [ ] 결과 파일 확인 (`results/results-phase2.json`)

---

## 결과 기록

### 성능 지표 비교

| 지표               | Phase 1 (인덱스 OFF) | Phase 2 (인덱스 ON) | 개선율        |
| ------------------ | -------------------- | ------------------- | ------------- |
| p50 latency        | 121 ms               | 104 ms              | -14.0%        |
| p95 latency        | 698 ms               | 655 ms              | -6.2%         |
| DB QPS             | Last 82.8, Max 128   | Last 93.2, Max 128  | +12.6% (Last) |
| Active Connections | 최대 10개            | 최대 1개            | -90%          |
| Execution Time     | 86.340 ms            | 0.028 ms            | -99.97%       |

**참고**:

- p50, p95 latency는 서버 응답 시간(`http_req_waiting`) 기준입니다.
- Execution Time은 EXPLAIN ANALYZE 결과입니다.

### EXPLAIN ANALYZE 비교

**Phase 1 결과**:

```
Seq Scan on public.url u  (cost=0.02..2280.77 rows=1 width=63) (actual time=0.177..86.177 rows=1 loops=1)
   Output: u.id, u.original_url, u.short_url, u.expiration_date, u.created_at
   Filter: ((u.short_url)::text = ($0)::text)
   Rows Removed by Filter: 99999
   Buffers: shared hit=1032
Planning Time: 3.236 ms
Execution Time: 86.340 ms
```

**Phase 2 결과**:

```
Index Scan using idx_url_short_url on public.url  (cost=0.29..8.31 rows=1 width=63) (actual time=0.016..0.017 rows=0 loops=1)
   Output: id, created_at, expiration_date, original_url, short_url
   Index Cond: ((url.short_url)::text = '7NT'::text)
   Buffers: shared hit=2
Planning Time: 0.054 ms
Execution Time: 0.028 ms
```

**비교 분석**:

| 항목                 | Phase 1   | Phase 2    | 변화         |
| -------------------- | --------- | ---------- | ------------ |
| Plan                 | Seq Scan  | Index Scan | ✅ 변경      |
| Execution Time       | 86.340 ms | 0.028 ms   | -99.97% 감소 |
| Planning Time        | 3.236 ms  | 0.054 ms   | -98.3% 감소  |
| Buffers (shared hit) | 1,032     | 2          | -99.8% 감소  |
| Rows Removed         | 99,999    | 0          | -100% 감소   |

**핵심 개선**:

- Execution Time이 86.340ms에서 0.028ms로 **99.97% 감소**하여 쿼리 실행 시간이 거의 즉시 완료됨
- Buffers 사용량이 1,032에서 2로 **99.8% 감소**하여 메모리 효율성 대폭 향상
- Rows Removed가 99,999에서 0으로 **100% 감소**하여 불필요한 스캔 완전 제거

### 인덱스 효과 결론

**인덱스 효과**:

- **Active Connections: 10개 → 1개 (-90% 감소)** - 가장 명확한 개선 지표
- **서버 응답 시간 (http_req_waiting)**:
  - p50 latency: 121 ms → 104 ms (-14.0% 개선)
  - p95 latency: 698 ms → 655 ms (-6.2% 개선)
- **DB QPS**: Last 82.8 → 93.2 (+12.6% 증가), Max 128 → 128 (동일)
- **Execution Time**: 86.340 ms → 0.028 ms (-99.97% 개선, EXPLAIN ANALYZE 기준)

**핵심 발견**:

- Active Connections이 90% 감소한 것이 인덱스 효과의 가장 명확한 지표입니다.
- 쿼리 실행 시간 단축(99.97% 개선)으로 커넥션 효율성이 대폭 향상되었습니다.
- 서버 응답 시간도 p50 14.0%, p95 6.2% 개선되었으나, 여전히 다른 병목이 존재함을 시사합니다.

---

## 산출물

- **k6 결과**: `results-phase2.json`
- **EXPLAIN ANALYZE 비교**: Phase1-vs-Phase2-explain.txt
- **성능 개선표**: 위 표 작성
- **인덱스 효과 결론**: 위 분석 작성

---

## 다음 단계

Phase 2 완료 후 → [Phase 3: 캐시 적용](./phase3-cache.md) 진행
