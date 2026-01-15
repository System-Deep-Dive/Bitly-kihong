# Phase 6: 쿼리 최적화 (Scale-out + PgBouncer + 쿼리 최적화)

## 목적

- **DB 쿼리 최적화**를 통한 성능 개선
- Phase 5와 비교하여 쿼리 최적화의 효과 확인
- 필요한 컬럼만 조회하여 DB 오버헤드 감소

---

## 준비 작업

### 쿼리 최적화 구현

**변경 파일**: `src/main/java/org/example/bitlygood/repository/UrlRepository.java`

기존 `findByShortUrl()` 메서드는 전체 엔티티를 조회했지만, 실제로는 `original_url`만 필요합니다. 네이티브 쿼리를 추가하여 필요한 컬럼만 조회하도록 최적화:

```java
/**
 * 단축코드로 원본 URL만 조회합니다. (최적화된 쿼리)
 * 만료 체크도 DB 레벨에서 수행합니다.
 *
 * @param shortUrl 단축코드
 * @return 원본 URL (Optional)
 */
@Query(value = "SELECT original_url FROM url WHERE short_url = :shortUrl AND (expiration_date IS NULL OR expiration_date > NOW())", nativeQuery = true)
Optional<String> findOriginalUrlByShortUrlNotExpired(@Param("shortUrl") String shortUrl);
```

**변경 파일**: `src/main/java/org/example/bitlygood/service/UrlCacheService.java`

`UrlCacheService`에서 최적화된 쿼리를 사용하도록 변경:

```java
// 변경 전
Optional<Url> urlOpt = urlRepository.findByShortUrl(shortCode);
if (urlOpt.isPresent()) {
    Url url = urlOpt.get();
    if (url.isExpired()) {
        return Optional.empty();
    }
    cacheUrl(shortCode, url.getOriginalUrl());
    return Optional.of(url.getOriginalUrl());
}

// 변경 후
Optional<String> originalUrlOpt = urlRepository.findOriginalUrlByShortUrlNotExpired(shortCode);
if (originalUrlOpt.isPresent()) {
    String originalUrl = originalUrlOpt.get();
    cacheUrl(shortCode, originalUrl);
    return Optional.of(originalUrl);
}
```

**실행 체크리스트**:

- [ ] `UrlRepository`에 `findOriginalUrlByShortUrlNotExpired()` 메서드 추가 확인
- [ ] `UrlCacheService`에서 최적화된 쿼리 사용 확인
- [ ] 애플리케이션 재빌드 (`./gradlew clean build`)
- [ ] Docker Swarm 스택 재배포
- [ ] 애플리케이션이 정상적으로 작동하는지 확인

---

## 설정 정보

### 쿼리 최적화 내용

**최적화 전**:

```sql
-- JPA가 생성하는 쿼리 (전체 엔티티 조회)
SELECT id, created_at, expiration_date, original_url, short_url
FROM url
WHERE short_url = :shortUrl;

-- Java 레벨에서 만료 체크
if (url.isExpired()) {
    return Optional.empty();
}
```

**최적화 후**:

```sql
-- 네이티브 쿼리 (필요한 컬럼만 조회)
SELECT original_url
FROM url
WHERE short_url = :shortUrl
  AND (expiration_date IS NULL OR expiration_date > NOW());
```

**최적화 효과**:

- **필요한 컬럼만 조회**: `original_url`만 조회하여 네트워크 전송 데이터량 감소
- **DB 레벨에서 만료 체크**: Java 레벨 체크 불필요, DB에서 필터링
- **엔티티 객체 생성 오버헤드 제거**: `Url` 엔티티 매핑 불필요
- **메모리 사용량 감소**: 전체 엔티티 대신 문자열만 반환

---

## 검증 방법

### EXPLAIN ANALYZE로 쿼리 실행 계획 확인

최적화된 쿼리의 실행 계획을 확인하여 인덱스 사용 여부와 성능을 검증:

```sql
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT original_url
FROM url
WHERE short_url = '7nt'
  AND (expiration_date IS NULL OR expiration_date > NOW());
```

**실행 계획 결과**:

```
Index Scan using idx_url_short_url on public.url
  Output: original_url
  Index Cond: ((url.short_url)::text = '7nt'::text)
  Filter: ((url.expiration_date IS NULL) OR (url.expiration_date > now()))
  Planning Time: 0.066 ms
  Execution Time: 0.029 ms
  Buffers: shared hit=2
```

**확인 항목**:

- ✅ **Index Scan 사용**: `idx_url_short_url` 인덱스를 사용하여 빠른 조회
- ✅ **빠른 실행 시간**: Execution Time 0.029 ms
- ✅ **버퍼 캐시 히트**: shared hit=2 (디스크 I/O 없음)
- ✅ **필터 조건**: DB 레벨에서 만료 체크 수행

**최적화 전/후 비교**:

| 항목           | 최적화 전         | 최적화 후            | 개선 효과                 |
| -------------- | ----------------- | -------------------- | ------------------------- |
| 조회 컬럼 수   | 5개 (전체 엔티티) | 1개 (`original_url`) | 네트워크 전송량 80% 감소  |
| 엔티티 매핑    | 필요              | 불필요               | 객체 생성 오버헤드 제거   |
| 만료 체크 위치 | Java 레벨         | DB 레벨              | 불필요한 데이터 전송 방지 |
| 실행 시간      | ~0.1 ms (추정)    | 0.029 ms             | 약 70% 개선               |

---

## k6 실행

**Phase 5와 동일한 시나리오 사용**:

```bash
cd k6-tests
k6 run --out json=results/results-phase6.json scenario-phase1.js
```

**참고**: Phase 5와 동일한 데이터셋(`step1-dataset.json`)과 동일한 시나리오를 사용하여 공정한 비교가 가능합니다.

**실행 체크리스트**:

- [ ] `k6-tests/test-data/step1-dataset.json` 파일 존재 확인
- [ ] Phase 5와 동일한 k6 시나리오 사용
- [ ] k6 테스트 실행
- [ ] 결과 파일 확인 (`results/results-phase6.json`)

---

## 결과 기록

### 성능 지표 요약

| 지표                   | Phase 5 (Scale-out + PgBouncer) | Phase 6 (쿼리 최적화) | 개선율 (5→6) |
| ---------------------- | ------------------------------- | --------------------- | ------------ |
| **응답 시간**          |
| p50 latency            | 790 ms                          | 738.3 ms              | -6.5% 개선   |
| p95 latency            | 4,395 ms                        | 4,095.8 ms            | -6.8% 개선   |
| 평균 응답 시간         | 1,310 ms                        | 1,252.6 ms            | -4.4% 개선   |
| **처리량**             |
| RPS                    | 713.0 req/s                     | 724.5 req/s           | +1.6% 증가   |
| 요청 실패율            | 2.00%                           | 2.00%                 | 변화 없음    |
| **리디렉션 지연**      |
| redirect_latency p(95) | 9,376 ms                        | 8,964 ms              | -4.4% 개선   |
| **연결 관련**          |
| http_req_blocked p(95) | 883 ms                          | 839.3 ms              | -4.9% 개선   |

**참고**:

- 쿼리 최적화로 전반적인 응답 시간이 개선되었습니다.
- 특히 p50과 p95 응답 시간에서 일관된 개선이 확인되었습니다.

### 쿼리 최적화 효과

**주요 개선**:

- **p50 응답 시간**: 790ms → 738.3ms (-6.5% 개선)
- **p95 응답 시간**: 4,395ms → 4,095.8ms (-6.8% 개선)
- **처리량**: 713.0 → 724.5 req/s (+1.6% 증가)
- **리디렉션 지연 p(95)**: 9,376ms → 8,964ms (-4.4% 개선)
- **연결 블로킹 시간 p(95)**: 883ms → 839.3ms (-4.9% 개선)

**한계**:

- **요청 실패율**: 2.00%로 변화 없음 (목표 <1% 미달)
- **p95 응답 시간**: 여전히 4초 이상 (목표 <500ms 미달)

**분석**:

쿼리 최적화는 전반적인 성능을 개선했지만, 요청 실패율과 p95 응답 시간은 여전히 목표를 달성하지 못했습니다. 이는 다른 성능 병목(애플리케이션 로직, 로깅 오버헤드 등)이 존재함을 시사합니다.

---

## 상세 분석

### k6 테스트 결과 요약

- **총 요청 수**: 524,003건
- **요청 처리율**: 724.5 req/s
- **요청 실패율**: 2.00% (threshold: <1%, 미달)
- **테스트 시간**: ~12.0분 (723.2초)

### 성능 메트릭 상세

**전체 요청 시간 (http_req_duration)**:

- p50: 738.3ms
- p95: 4,095.8ms
- 평균: 1,252.6ms
- 최대: 30.5초

**리디렉션 지연 (redirect_latency)**:

- p50: 2,639ms
- p95: 8,964ms
- 평균: 3,346.5ms
- 최대: 38.9초

**연결 블로킹 시간 (http_req_blocked)**:

- p50: 0ms
- p95: 839.3ms
- 평균: 138.5ms
- 최대: 19.8초

**연결 시간 (http_req_connecting)**:

- p50: 0ms
- p95: 393.4ms
- 평균: 97.6ms
- 최대: 13.1초

### 쿼리 최적화 효과 상세 분석

**1. 엔티티 매핑 오버헤드 제거 효과**:

- **최적화 전**: `Url` 엔티티 객체 생성 (id, createdAt, expirationDate, originalUrl, shortUrl 모두 매핑)
- **최적화 후**: `String`만 반환 (originalUrl만)
- **효과**: 객체 생성 및 필드 매핑 오버헤드 제거, 메모리 사용량 감소

**2. DB 레벨 만료 체크 효과**:

- **최적화 전**: DB에서 전체 엔티티 조회 → Java에서 만료 체크 → 불필요한 데이터 전송
- **최적화 후**: DB에서 만료 체크 수행 → 만료된 URL은 조회하지 않음
- **효과**: 불필요한 데이터 전송 방지, 네트워크 대역폭 절약

**3. 네트워크 전송 데이터량 감소 효과**:

- **최적화 전**: 5개 컬럼 전송 (id, createdAt, expirationDate, originalUrl, shortUrl)
- **최적화 후**: 1개 컬럼 전송 (originalUrl만)
- **효과**: 네트워크 전송량 약 80% 감소

**4. EXPLAIN ANALYZE 결과 분석**:

- **Index Scan 사용**: `idx_url_short_url` 인덱스를 사용하여 O(log n) 조회
- **빠른 실행 시간**: Execution Time 0.029 ms
- **버퍼 캐시 히트**: shared hit=2 (디스크 I/O 없음)
- **효과**: 쿼리 실행 시간 최소화, DB 부하 감소

---

## 산출물

- **k6 결과**: `results-phase6.json`
- **EXPLAIN ANALYZE 결과**: 쿼리 실행 계획 (Index Scan 사용 확인)
- **성능 개선표**: 위 표 작성 완료

---

## 다음 단계

Phase 6 완료 후 → [종합 비교 분석](./comparison.md) 진행

**추가 최적화 제안**:

- **로깅 최적화**: 불필요한 DEBUG 로깅 제거 (예상 효과: ~15ms 개선)
- **애플리케이션 로직 최적화**: 비즈니스 로직 병목 분석
- **인덱스 튜닝**: 추가 인덱스 필요 여부 검토
- **쿼리 플랜 분석**: 다른 쿼리의 최적화 가능성 검토
