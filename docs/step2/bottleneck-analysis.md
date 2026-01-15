# Phase 6 병목 분석 보고서

## 목적

k6 테스트 결과(`results-phase6.json`)를 기반으로 Phase 6의 성능 병목 지점을 파악하고, 다양한 모니터링 도구를 활용하여 실제 병목 원인을 분석합니다.

---

## k6 테스트 결과 요약

### 주요 성능 지표 (p95 기준)

| 지표                                    | 값         | 비고               |
| --------------------------------------- | ---------- | ------------------ |
| **전체 요청 시간** (http_req_duration)  | 4,095.8 ms | 목표 500ms 미달    |
| **서버 응답 시간** (http_req_waiting)   | 1,611.4 ms | **가장 큰 병목**   |
| **응답 수신 시간** (http_req_receiving) | 2,535.8 ms | 두 번째 병목       |
| **연결 블로킹 시간** (http_req_blocked) | 839.3 ms   | 연결 대기 시간     |
| **연결 시간** (http_req_connecting)     | 393.4 ms   | TCP 연결 시간      |
| **리디렉션 지연** (redirect_latency)    | 8,964 ms   | 클라이언트 측 처리 |

### 시간 구성 분석

**전체 요청 시간 (4,095ms p95) 구성**:

- 서버 응답 시간: 1,611ms (39.3%)
- 응답 수신 시간: 2,536ms (61.9%)
- 연결 블로킹: 839ms (20.5%)
- 연결 시간: 393ms (9.6%)

**주요 발견**:

- 서버 응답 시간(1,611ms)이 전체 응답 시간의 약 40%를 차지
- 응답 수신 시간(2,536ms)이 가장 큰 비중을 차지 (네트워크 또는 응답 크기 문제 가능)

---

## 모니터링 도구별 분석 결과

### 1. Spring Boot Actuator 메트릭

#### HTTP 요청 메트릭

```json
{
  "name": "http.server.requests",
  "baseUnit": "seconds",
  "measurements": [
    { "statistic": "COUNT", "value": 159428.0 },
    { "statistic": "TOTAL_TIME", "value": 347.439110399 },
    { "statistic": "MAX", "value": 0.0 }
  ]
}
```

**분석**:

- 총 요청 수: 159,428건
- 총 처리 시간: 347.44초
- 평균 요청 처리 시간: 347.44 / 159,428 = **2.18ms** (매우 빠름)
- 최대값이 0.0인 것은 메트릭 수집 시점의 문제일 수 있음

**URI별 분류**:

- `/actuator/prometheus`
- `/urls` (POST)
- `/{shortUrl}` (GET) - 리디렉션 엔드포인트

#### HikariCP 연결 풀 메트릭

```json
{
  "name": "hikaricp.connections.active",
  "value": 0.0
}

{
  "name": "hikaricp.connections.pending",
  "value": 0.0
}
```

**분석**:

- 활성 연결: 0개 (유휴 상태)
- 대기 중인 연결: 0개
- **결론**: 연결 풀 경합은 현재 발생하지 않음 (PgBouncer 효과)

#### JVM 메모리 메트릭

```json
{
  "name": "jvm.memory.used",
  "baseUnit": "bytes",
  "value": 1.07418672e8 // 약 102MB
}
```

**분석**:

- 힙 메모리 사용량: 약 102MB
- 정상 범위 내 (메모리 부족 아님)

#### GC 메트릭

```json
{
  "name": "jvm.gc.pause",
  "baseUnit": "seconds",
  "measurements": [
    { "statistic": "COUNT", "value": 175.0 },
    { "statistic": "TOTAL_TIME", "value": 5.493 },
    { "statistic": "MAX", "value": 0.0 }
  ]
}
```

**분석**:

- GC 발생 횟수: 175회
- 총 GC 시간: 5.493초
- 평균 GC 시간: 5.493 / 175 = **31.4ms**
- **결론**: GC는 병목이 아님 (평균 31ms는 허용 범위)

#### Redis (Lettuce) 메트릭

```json
{
  "name": "lettuce.command.completion",
  "baseUnit": "seconds",
  "measurements": [
    { "statistic": "COUNT", "value": 321660.0 },
    { "statistic": "TOTAL_TIME", "value": 169.41286111 },
    { "statistic": "MAX", "value": 0.0 }
  ]
}
```

**분석**:

- 총 Redis 명령 수: 321,660건
- 총 처리 시간: 169.41초
- 평균 Redis 응답 시간: 169.41 / 321,660 = **0.53ms** (매우 빠름)
- **결론**: Redis는 병목이 아님

**명령별 분류**:

- `SET`, `HELLO`, `CLIENT`, `INCR`, `GET`

### 2. 캐시 통계

```json
{
  "hitRatePercentage": "89.81%",
  "hitRate": 0.8981455554709306,
  "status": "healthy"
}
```

**분석**:

- 캐시 히트율: **89.81%** (목표 80%+ 달성)
- 대부분의 요청이 Redis에서 처리됨
- **결론**: 캐시 전략이 효과적으로 작동 중

### 3. Redis 성능 분석

#### Redis SLOWLOG

```
1) 1) (integer) 1768393049  // 실행 시간 (Unix timestamp)
   2) (integer) 14527        // 실행 시간 (마이크로초)
   3) (integer) 14527        // 실행 시간 (마이크로초)
   4) 1) "GET"
      2) "url:4KT"
   5) "10.0.1.26:43160"     // 클라이언트 주소
   6) ""
```

**분석**:

- 가장 느린 명령: `GET url:4KT` - 14.5ms
- 대부분의 명령이 10ms 이하로 매우 빠름
- **결론**: Redis 성능은 정상 범위

#### Redis 통계

```
total_commands_processed: 3,500,183
instantaneous_ops_per_sec: 0 (유휴 상태)
keyspace_hits: 1,548,704
keyspace_misses: 160,133
```

**분석**:

- 총 명령 수: 3,500,183건
- 캐시 히트: 1,548,704건
- 캐시 미스: 160,133건
- 히트율: 1,548,704 / (1,548,704 + 160,133) = **90.6%**
- **결론**: Redis 캐시가 효과적으로 작동 중

### 4. PostgreSQL 쿼리 성능 분석

**참고**: `pg_stat_statements` 확장이 활성화되지 않아 쿼리별 상세 분석은 불가능했습니다.

**대안 분석 방법**:

- EXPLAIN ANALYZE 결과 (Phase 6 문서 참조)
  - 쿼리 실행 시간: 0.029ms (매우 빠름)
  - 인덱스 스캔 사용: `idx_url_short_url`
  - 버퍼 캐시 히트: shared hit=2

**결론**: DB 쿼리는 최적화되어 있으며 병목이 아님

---

## 병목 지점 종합 분석

### 확인된 병목 지점

#### 1. 서버 응답 시간 (http_req_waiting) - 1,611ms p95

**현재 상태**:

- Actuator 메트릭: 평균 2.18ms (매우 빠름)
- 하지만 k6 결과: p95 1,611ms (매우 느림)

**분석**:

- 평균은 빠르지만, 일부 요청이 매우 느림 (p95)
- **가능한 원인**:
  1. **스레드 풀 경합**: 10개 인스턴스 × 500 스레드 = 5,000 스레드
  2. **로깅 오버헤드**: DEBUG 로그가 활성화되어 있을 수 있음
  3. **네트워크 지연**: 인스턴스 간 통신 지연
  4. **로드 밸런서 지연**: Docker Swarm 내부 로드 밸런싱 지연

#### 2. 응답 수신 시간 (http_req_receiving) - 2,536ms p95

**분석**:

- 응답을 받는 데 걸리는 시간이 매우 김
- **가능한 원인**:
  1. **응답 크기**: 리디렉션 응답은 작지만, 네트워크 지연 가능
  2. **네트워크 대역폭**: Docker Swarm 오버레이 네트워크 지연
  3. **클라이언트 측 처리**: k6 클라이언트의 응답 처리 지연

#### 3. 연결 블로킹 시간 (http_req_blocked) - 839ms p95

**분석**:

- 연결을 기다리는 시간
- **가능한 원인**:
  1. **로드 밸런서 대기**: Docker Swarm 내부 로드 밸런서의 연결 대기
  2. **스레드 풀 포화**: 모든 스레드가 사용 중일 때 대기

### 제외된 병목 지점

#### ✅ DB 쿼리 시간

- EXPLAIN ANALYZE: 0.029ms
- 쿼리 최적화 완료
- **결론**: 병목 아님

#### ✅ Redis 응답 시간

- 평균: 0.53ms
- SLOWLOG: 최대 14.5ms
- **결론**: 병목 아님

#### ✅ GC 시간

- 평균: 31.4ms
- 총 GC 시간: 5.493초 (전체 시간 대비 미미)
- **결론**: 병목 아님

#### ✅ 연결 풀 경합

- HikariCP 활성 연결: 0개
- 대기 중인 연결: 0개
- PgBouncer 효과 확인
- **결론**: 병목 아님

---

## 추가 확인이 필요한 사항

### 1. 성능 측정 로그 추가

`UrlCacheService`에 성능 측정 로그를 추가했습니다:

```java
// 캐시 조회 시간 측정
long cacheTime = (System.nanoTime() - cacheStartTime) / 1_000_000; // ms

// DB 쿼리 시간 측정
long dbTime = (System.nanoTime() - dbStartTime) / 1_000_000; // ms

// 느린 작업 감지 (경고 로그)
if (cacheTime > 10) {
    log.warn("Slow cache lookup detected - shortCode: {}, cacheTime: {}ms",
        shortCode, cacheTime);
}

if (dbTime > 50) {
    log.warn("Slow DB query detected - shortCode: {}, dbTime: {}ms",
        shortCode, dbTime);
}
```

**다음 단계**:

1. 애플리케이션 재빌드 및 재배포
2. k6 테스트 재실행
3. 애플리케이션 로그에서 느린 작업 확인

### 2. Prometheus 상세 메트릭 확인

**Prometheus 쿼리 예시**:

```promql
# HTTP 요청 시간 (p95)
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# JVM 스레드 수
jvm_threads_live_threads

# Tomcat 스레드 풀 상태
tomcat_threads_current_threads

# Redis 응답 시간
histogram_quantile(0.95, rate(lettuce_command_completion_seconds_bucket[5m]))
```

**접속 방법**:

- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)

### 3. 애플리케이션 로그 분석

**확인할 로그**:

- `Slow cache lookup detected` - 10ms 이상의 캐시 조회
- `Slow DB query detected` - 50ms 이상의 DB 쿼리
- 일반적인 에러 로그

**로그 확인 방법**:

```bash
# Docker Swarm 로그 확인
docker service logs bitly-stack_app --tail 1000 | Select-String -Pattern "Slow|Error|WARN"
```

### 4. 네트워크 지연 분석

**확인 사항**:

- Docker Swarm 오버레이 네트워크 지연
- 로드 밸런서 지연
- 인스턴스 간 통신 지연

**분석 방법**:

- 네트워크 트레이싱 도구 사용
- 각 인스턴스의 응답 시간 개별 측정

---

## 권장 개선 방안

### 1. 로깅 최적화 (우선순위: 높음)

**현재 상태**:

- DEBUG 로그가 활성화되어 있을 수 있음
- 매 요청마다 로그 출력으로 오버헤드 발생

**개선 방안**:

```properties
# application-docker.properties
logging.level.org.example.bitlygood=INFO
logging.level.org.springframework=WARN
```

**예상 효과**: 10-50ms 개선 가능

### 2. 스레드 풀 최적화 (우선순위: 중간)

**현재 설정**:

```properties
server.tomcat.threads.max=500
server.tomcat.threads.min-spare=50
```

**분석**:

- 10개 인스턴스 × 500 스레드 = 5,000 스레드
- 과도한 스레드 수로 인한 컨텍스트 스위칭 오버헤드 가능

**개선 방안**:

- 스레드 수를 200-300으로 조정
- 실제 부하에 맞게 튜닝

### 3. 네트워크 최적화 (우선순위: 중간)

**개선 방안**:

- Docker Swarm 오버레이 네트워크 대신 호스트 네트워크 사용 검토
- 로드 밸런서 설정 최적화

### 4. 응답 크기 최적화 (우선순위: 낮음)

**현재 상태**:

- 리디렉션 응답은 작지만, HTTP 헤더 최적화 가능

**개선 방안**:

- 불필요한 HTTP 헤더 제거
- 압축 활성화 (gzip)

---

## 결론

### 확인된 사실

1. **DB 쿼리**: 최적화 완료, 병목 아님 (0.029ms)
2. **Redis**: 매우 빠름, 병목 아님 (0.53ms 평균)
3. **GC**: 정상 범위, 병목 아님 (31.4ms 평균)
4. **연결 풀**: PgBouncer 효과 확인, 병목 아님

### 주요 병목 지점

1. **서버 응답 시간** (1,611ms p95)

   - 평균은 빠르지만 일부 요청이 매우 느림
   - 스레드 풀 경합 또는 로깅 오버헤드 가능

2. **응답 수신 시간** (2,536ms p95)

   - 네트워크 지연 또는 클라이언트 측 처리 지연 가능

3. **연결 블로킹 시간** (839ms p95)
   - 로드 밸런서 또는 스레드 풀 포화 가능

### 다음 단계

1. ✅ 성능 측정 로그 추가 완료
2. ⏳ 애플리케이션 재빌드 및 재배포
3. ⏳ k6 테스트 재실행
4. ⏳ 로그 분석으로 실제 병목 지점 확인
5. ⏳ Prometheus/Grafana 대시보드로 상세 분석

---

## 참고 자료

- [k6 테스트 결과](../k6-tests/results/results-phase6.json)
- [Phase 6 문서](./phase6-query-optimization.md)
- [비교 분석 문서](./comparison.md)
- [Spring Boot Actuator 문서](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
