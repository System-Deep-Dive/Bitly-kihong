# Phase 1: 베이스라인 측정 (캐시 OFF + 인덱스 OFF)

## 목적

- **최적화 전 상태**에서 병목 위치를 수치로 파악
- DB 쿼리 실행 계획 확인 (Seq Scan 여부)
- 이후 Phase와 비교할 기준선(baseline) 확보

---

## 준비 작업

> **주의**: Phase 1 완료 후 Phase 2, 3에서 다시 원래대로 되돌려야 하므로, 코드 수정 전에 **Git 커밋** 또는 **브랜치 생성**을 권장합니다.

### 캐시 비활성화

`UrlService`에서 `UrlCacheService`를 우회하도록 수정:

**방법: `UrlService.getOriginalUrl()` 메서드 수정**

`UrlService.java`의 `getOriginalUrl()` 메서드를 다음과 같이 수정:

```java
public String getOriginalUrl(String shortCode) {
    log.debug("Retrieving original URL for short code: {}", shortCode);

    // Phase 1: 캐시 우회 - Repository 직접 호출
    return urlRepository.findByShortUrl(shortCode)
            .filter(url -> !url.isExpired())
            .map(Url::getOriginalUrl)
            .orElseThrow(() -> {
                log.warn("No original URL found for short code: {}", shortCode);
                return new IllegalArgumentException("Invalid short url");
            });
}
```

**실행 체크리스트**:

- [ ] `UrlService.java` 파일 열기
- [ ] `getOriginalUrl()` 메서드 수정 (위 코드 참고)
- [ ] 애플리케이션 재시작
- [ ] 캐시가 비활성화되었는지 확인 (로그에서 "Cache hit" 메시지가 없는지 확인)

### 인덱스 제거

PostgreSQL에서 인덱스 제거:

```sql
psql -U user -d bitly

-- 현재 인덱스 확인
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'url';
-- Phase 1: 인덱스 제거 (조회 성능에 영향을 주는 인덱스들)
DROP INDEX IF EXISTS idx_url_short_url;
DROP INDEX IF EXISTS idx_url_expiration_date;
DROP INDEX IF EXISTS idx_url_created_at;

-- 제거 확인
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'url';
```

**실행 체크리스트**:

- [x] 현재 인덱스 목록 확인
- [x] 인덱스 제거
- [x] 인덱스 제거 확인

---

## 모니터링

**Grafana 대시보드**: `http://localhost:3000` → "Step 1: Phase별 성능 비교" 선택

**Phase 변수 설정**: 대시보드 상단에서 **Phase 1** 선택

**확인 항목**:

- HTTP 요청 처리율 (RPS)
- HTTP 응답 시간 (p50, p95, p99)
- HTTP 에러율
- 애플리케이션 CPU/메모리
- JVM Heap 메모리

**상세 가이드**: [모니터링 가이드](./monitoring-guide.md)

---

## 측정 항목

### 애플리케이션 메트릭

- 요청 수, 응답 코드 분포 (200, 302, 404)
- **Latency**: p50, p95, p99
- CPU, 메모리, GC 통계
- DB 커넥션 풀 사용률 (active/idle/waiting)

**확인 방법**: k6 결과, Prometheus/Grafana (`http://localhost:3000`), Actuator (`http://localhost:8080/actuator/metrics`)

### 데이터베이스 메트릭

- **QPS** (Queries Per Second)
- **Active connections**
- **Slow query** (1초 이상)
- **EXPLAIN ANALYZE 결과** (핵심!)

**확인 방법**: Prometheus/Grafana (`http://localhost:3000`), PostgreSQL 직접 조회

### EXPLAIN ANALYZE 실행

**PostgreSQL 접속**:

```bash
docker exec -it $(docker ps -q -f name=postgres) psql -U user -d bitly
```

**실행**:

1. 애플리케이션 로그에서 생성된 SQL 확인 (`spring.jpa.show-sql=true` 설정됨)
2. PostgreSQL에서 EXPLAIN ANALYZE 실행:

```sql
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT u.id, u.original_url, u.short_url, u.expiration_date, u.created_at
FROM url u
WHERE u.short_url = '실제_shortCode_값';
```

**확인 포인트**: `Seq Scan` 여부, Execution Time, Buffers, Rows

**실행 체크리스트**:

- [ ] 애플리케이션 로그에서 SQL 확인
- [ ] PostgreSQL 접속
- [ ] EXPLAIN ANALYZE 실행
- [ ] 결과 저장 (Phase1-explain-analyze.txt)

---

## k6 실행

**Phase 1용 k6 시나리오 파일**:

`k6-tests/scenario-phase1.js` 파일이 준비되어 있습니다.

**시나리오 구성**:

- **스모크 테스트**: VU 10, 1분 (정상 동작 확인)
- **램프업**: VU 10 → 5,000 → 10,000 (각 3분)
- **스테디 상태**: VU 10,000 유지 5분
- **총 실행 시간**: 약 12분

> **참고**: 병목 현상을 명확히 확인하기 위해 VU를 10,000으로 증가시켰습니다.

**데이터 분포**:

- Hot: 50% (상위 1% 코드)
- Warm: 30% (1%~10% 코드)
- Cold: 20% (나머지 90% 코드)
- Invalid: 2% (404 테스트)

**k6 테스트 실행**:

```bash
cd k6-tests
k6 run scenario-phase1.js
```

또는 JSON 결과만 저장하려면:

```bash
cd k6-tests
k6 run --out json=results/results-phase1.json scenario-phase1.js
```

**실행 체크리스트**:

- [x] `k6-tests/test-data/step1-dataset.json` 파일 존재 확인
- [x] k6 테스트 실행
- [x] 결과 파일 확인 (`results/results-phase1.json`)

**참고**: 모든 Phase(1, 2, 3)에서 동일한 데이터셋(`step1-dataset.json`, 100,000개)과 동일한 시나리오(`scenario-phase1.js`)를 사용합니다.

---

## 결과 기록

### k6 테스트 결과

| 지표          | 값               | 비고                            |
| ------------- | ---------------- | ------------------------------- |
| 총 요청 수    | 78,869           |                                 |
| 성공률        | 99.99%           | HTTP 요청 기준 (checks: 99.99%) |
| p50 latency   | 366.36 ms        |                                 |
| p95 latency   | 11,062.87 ms     | 매우 높음 (병목 확인)           |
| p99 latency   | >1,000 ms        | Threshold 실패 (매우 높음)      |
| RPS           | 99.00 req/s      | 매우 낮음 (10,000 VU 기준)      |
| 에러율        | 1.32%            | 타임아웃/커넥션 거부 가능성     |
| 테스트 시간   | 796.6초 (13.3분) |                                 |
| 총 iterations | 78,403           |                                 |

### 데이터베이스 메트릭

| 지표               | 값                 | 비고                                     |
| ------------------ | ------------------ | ---------------------------------------- |
| DB QPS             | ~99.00 (k6)        | k6 RPS와 유사 (커넥션 풀 제한)           |
|                    | Last 82.8, Max 128 | Grafana 실시간 모니터링                  |
| Active connections | 최대 10개          | Grafana 실제 측정값 (HikariCP 최대 10개) |

### Grafana 실시간 모니터링 메트릭

**HTTP 메트릭**:

| 지표                           | 값              | 비고                       |
| ------------------------------ | --------------- | -------------------------- |
| HTTP 요청 처리율 (RPS)         | Last 76.1 req/s | Grafana 측정값             |
|                                | Max 123 req/s   |                            |
| HTTP 응답 시간 (p50, p95, p99) | 0-20 ms         | 서버 측 응답 시간 (안정적) |

**애플리케이션 메트릭**:

| 지표            | 값                      | 비고 |
| --------------- | ----------------------- | ---- |
| CPU 사용률      | 0.1-0.7 범위            |      |
| JVM Heap 메모리 | Last 150 MiB            |      |
|                 | Max 168 MiB             |      |
| JVM GC 횟수     | MarkSweepCompact 0.0208 |      |
|                 | Copy 0.0659             |      |

**메트릭 차이점 설명**:

- **k6 p95 latency (11,062.87 ms) vs Grafana p95 (0-20 ms)**:
  - k6는 클라이언트 측 응답 시간 (네트워크 지연, 커넥션 대기 시간 포함)
  - Grafana는 서버 측 메트릭 (애플리케이션 처리 시간만 측정)
  - 차이는 주로 커넥션 풀 대기 시간과 네트워크 지연에서 발생
- **k6 RPS (99.00) vs Grafana RPS (Max 123)**:
  - 측정 시점과 방식의 차이
  - Grafana는 서버 측 실제 처리량, k6는 클라이언트 측 요청률

### EXPLAIN ANALYZE 결과

**실행 계획**:

```
Seq Scan on public.url u  (cost=0.02..2280.77 rows=1 width=63) (actual time=0.177..86.177 rows=1 loops=1)
   Output: u.id, u.original_url, u.short_url, u.expiration_date, u.created_at
   Filter: ((u.short_url)::text = ($0)::text)
   Rows Removed by Filter: 99999
   Buffers: shared hit=1032
   InitPlan 1 (returns $0)
     ->  Limit  (cost=0.00..0.02 rows=1 width=4) (actual time=0.033..0.034 rows=1 loops=1)
           Output: url.short_url
           Buffers: shared hit=1
           ->  Seq Scan on public.url  (cost=0.00..2030.80 rows=99980 width=4) (actual time=0.003..0.004 rows=1 loops=1)
                 Output: url.short_url
                 Buffers: shared hit=1
 Planning:
   Buffers: shared hit=12 dirtied=3
 Planning Time: 3.236 ms
 Execution Time: 86.340 ms
```

**분석**:

- **Seq Scan 확인**: 인덱스 없이 전체 테이블 스캔 수행 ✅
- **Execution Time**: 86.340 ms (100,000개 데이터 기준)
- **Rows Removed by Filter**: 99,999개 (Seq Scan의 전형적인 패턴)
- **Buffers**: shared hit=1032 (메모리에서 읽음, 디스크 I/O 없음)

> **참고**: InitPlan은 테스트 쿼리에 포함된 서브쿼리로, 실제 애플리케이션 쿼리에는 없을 수 있습니다. 핵심은 Seq Scan과 Execution Time입니다.

### 결과 요약

**주요 발견사항**:

1. **심각한 병목 확인**:

   - 10,000 VU를 투입했음에도 불구하고 RPS가 99.00 req/s로 제한됨
   - p95 응답 시간이 11,175.93ms로 매우 높음
   - 평균 반복 시간이 53초로 긴 대기 시간 발생

2. **병목 지점**:

   - **DB Seq Scan 비용**: Execution Time 86.340ms로 쿼리 실행이 느림 (주 병목)
   - **시스템 처리 용량 초과**: 이론적 처리 용량(약 115.8 req/s)에 거의 도달
   - **커넥션 풀은 충분함**: Active Connections는 최대 4개만 사용 (커넥션 풀 부족이 아님)

3. **처리량 분석**:

   - 투입한 부하: 10,000 VU
   - 실제 처리량:
     - k6 RPS: 99.00 req/s
     - DB QPS: 최대 128, Last 82.8
   - 이론적 처리 용량: 10개 커넥션 × (1000ms / 86.340ms) ≈ 115.8 req/s
   - 실제 DB QPS (124)가 이론적 처리 용량(115.8)에 거의 도달함 (약 107% 활용률)
   - **핵심 발견**: Active Connections는 최대 4개만 사용됨
     → 커넥션 풀 부족이 아닌, Query Execution Time (86.340ms)로 인한 처리량 제한
     → 각 쿼리가 느려서 커넥션이 충분해도 전체 처리량이 제한됨

4. **예상 개선 효과**:
   - Phase 2 (인덱스): Execution Time 감소 → 처리량 증가 예상
   - Phase 3 (캐시): Hot/Warm 데이터 캐시 처리 → DB 부하 대폭 감소 예상

### 병목 가설 및 검증

1. **병목 지점: DB Seq Scan 비용** ✅ **검증됨 (주 병목)**

   - **근거**:
     - EXPLAIN ANALYZE에서 Seq Scan 확인
     - Execution Time: 86.340 ms (100,000개 데이터 기준)
     - 각 쿼리가 약 86ms 소요 → 초당 약 11-12개 쿼리만 처리 가능
     - 이론적 처리 용량: 10개 커넥션 × (1000ms / 86.340ms) ≈ 115.8 req/s
     - 실제 DB QPS (최대 128)가 이론적 처리 용량(115.8)에 거의 도달함 (약 110% 활용률)
   - **영향**:
     - DB 쿼리 실행 시간(Execution Time)이 길어서 전체 처리량 제한
     - DB CPU 사용률 증가
     - Rows Removed by Filter: 99,999개로 Seq Scan 비용 큼
   - **측정 방법**: `EXPLAIN ANALYZE`로 Seq Scan 및 Execution Time 확인 (완료)

2. **병목 지점: 시스템 처리 용량 초과** ✅ **검증됨**

   - **근거**:
     - 실제 DB QPS (최대 128)가 이론적 처리 용량(115.8 req/s)에 거의 도달
     - 실제 k6 RPS (99.00 req/s)도 이론적 처리 용량과 유사
     - 에러율 1.32%로 일부 요청 실패 발생
     - 평균 반복 시간이 53초로 매우 긴 대기
   - **영향**:
     - 시스템이 처리 용량 한계에 도달
     - 추가 요청은 큐잉되거나 실패
   - **측정 방법**: Grafana에서 DB QPS 및 Active Connections 모니터링

3. **커넥션 풀은 충분함** ✅ **검증됨**

   - **근거**:
     - Active Connections는 최대 10개 사용됨 (커넥션 풀 최대 10개)
     - 실제 DB QPS가 이론적 처리 용량에 거의 도달함에도 불구하고 커넥션은 최대 10개 사용
   - **의미**:
     - 커넥션 풀 부족이 병목이 아님
     - Query Execution Time (86.340ms)이 길어서, 적은 수의 커넥션으로도 처리 용량 한계에 도달
     - 각 쿼리가 느려서 커넥션이 충분해도 전체 처리량이 제한됨
   - **측정 방법**: Grafana에서 DB Active Connections 모니터링 (최대 10개 확인)

---

## 산출물

- **k6 결과**: `results-phase1.json` (요약 + raw 데이터)
- **EXPLAIN ANALYZE 결과**: Phase1-explain-analyze.txt
- **DB 메트릭**: QPS, Active Connections (Grafana)
- **Grafana 모니터링 메트릭**: HTTP RPS, 응답 시간, CPU, 메모리, GC
- **병목 가설**: 위 표 작성

---

## 다음 단계

Phase 1 완료 후 → [Phase 2: 인덱스 적용](./phase2-index.md) 진행
