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

```bash
cd k6-tests
k6 run --out json=results-phase2.json scenario-phase1.js
# 동일한 시나리오 사용 (변수는 인덱스만 변경)
```

**실행 체크리스트**:

- [ ] Phase 1과 동일한 k6 시나리오 사용
- [ ] k6 테스트 실행
- [ ] 결과 파일 저장 (`results-phase2.json`)

---

## 결과 기록

### 성능 지표 비교

| 지표           | Phase 1 (인덱스 OFF) | Phase 2 (인덱스 ON) | 개선율 |
| -------------- | -------------------- | ------------------- | ------ |
| p50 latency    | ? ms                 | ? ms                | ?%     |
| p95 latency    | ? ms                 | ? ms                | ?%     |
| p99 latency    | ? ms                 | ? ms                | ?%     |
| DB QPS         | ?                    | ?                   | ?%     |
| DB CPU         | ?%                   | ?%                  | ?%     |
| Execution Time | ? ms                 | ? ms                | ?%     |

### EXPLAIN ANALYZE 비교

**Phase 1 결과**:

```
[Phase 1의 EXPLAIN ANALYZE 결과 붙여넣기]
```

**Phase 2 결과**:

```
[Phase 2의 EXPLAIN ANALYZE 결과 붙여넣기]
```

**비교 분석**:

| 항목                  | Phase 1  | Phase 2    | 변화    |
| --------------------- | -------- | ---------- | ------- |
| Plan                  | Seq Scan | Index Scan | ✅ 변경 |
| Execution Time        | ? ms     | ? ms       | ?% 감소 |
| Buffers (shared hit)  | ?        | ?          | ?% 증가 |
| Buffers (shared read) | ?        | ?          | ?% 감소 |
| Rows scanned          | ?        | ?          | ?% 감소 |

### 인덱스 효과 결론

**인덱스 효과**:

- p95 latency: ? ms → ? ms (?% 개선)
- DB CPU: ?% → ?% (?% 감소)
- Execution Time: ? ms → ? ms (?% 개선)

---

## 산출물

- **k6 결과**: `results-phase2.json`
- **EXPLAIN ANALYZE 비교**: Phase1-vs-Phase2-explain.txt
- **성능 개선표**: 위 표 작성
- **인덱스 효과 결론**: 위 분석 작성

---

## 다음 단계

Phase 2 완료 후 → [Phase 3: 캐시 적용](./phase3-cache.md) 진행
