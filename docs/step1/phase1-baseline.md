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
- **램프업**: VU 10 → 500 → 1,000 (각 2분)
- **스테디 상태**: VU 1,000 유지 3분
- **총 실행 시간**: 약 10분

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

- [ ] `k6-tests/test-data/step1-dataset.json` 파일 존재 확인
- [ ] k6 테스트 실행
- [ ] 결과 파일 확인 (`results/results-phase1.json`)

**참고**: 모든 Phase(1, 2, 3)에서 동일한 데이터셋(`step1-dataset.json`)과 동일한 시나리오(`scenario-phase1.js`)를 사용합니다.

---

## 결과 기록

### k6 테스트 결과

| 지표        | 값   | 비고 |
| ----------- | ---- | ---- |
| 총 요청 수  | ?    |      |
| 성공률      | ?%   |      |
| p50 latency | ? ms |      |
| p95 latency | ? ms |      |
| p99 latency | ? ms |      |
| RPS         | ?    |      |
| 에러율      | ?%   |      |

### 데이터베이스 메트릭

| 지표               | 값  | 비고 |
| ------------------ | --- | ---- |
| DB QPS             | ?   |      |
| Active connections | ?   |      |
| DB CPU 사용률      | ?%  |      |
| Slow query 수      | ?   |      |

### EXPLAIN ANALYZE 결과

**실행 계획**:

```
[여기에 EXPLAIN ANALYZE 결과 붙여넣기]
```

### 병목 가설

1. **병목 지점**: ?

   - **근거**: ?
   - **영향**: ?

2. **병목 지점**: ?

   - **근거**: ?
   - **영향**: ?

3. **병목 지점**: ?
   - **근거**: ?
   - **영향**: ?

---

## 산출물

- **k6 결과**: `results-phase1.json` (요약 + raw 데이터)
- **EXPLAIN ANALYZE 결과**: Phase1-explain-analyze.txt
- **DB 메트릭**: CPU 사용률, QPS, 커넥션 수
- **병목 가설**: 위 표 작성

---

## 다음 단계

Phase 1 완료 후 → [Phase 2: 인덱스 적용](./phase2-index.md) 진행
