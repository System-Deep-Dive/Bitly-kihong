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
-- 현재 인덱스 확인
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'url';

-- 인덱스 제거
DROP INDEX IF EXISTS idx_url_short_url;

-- 제거 확인
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'url';
```

**실행 체크리스트**:

- [ ] PostgreSQL에 접속
- [ ] 현재 인덱스 목록 확인
- [ ] `idx_url_short_url` 인덱스 제거
- [ ] 인덱스 제거 확인

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

**Phase 1용 k6 시나리오 파일 생성**:

기존 `scenario-b-minimal.js`를 복사하여 `scenario-phase1.js`로 생성:

```bash
cd k6-tests
cp scenario-b-minimal.js scenario-phase1.js
```

`scenario-phase1.js`는 `scenario-b-minimal.js`와 동일하게 사용 (수정 불필요)

**k6 테스트 실행**:

```bash
cd k6-tests
k6 run --out json=results-phase1.json scenario-phase1.js
```

**실행 체크리스트**:

- [ ] `scenario-b-minimal.js`를 복사하여 `scenario-phase1.js` 생성
- [ ] k6 테스트 실행
- [ ] 결과 파일 확인 (`results-phase1.json`)

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
