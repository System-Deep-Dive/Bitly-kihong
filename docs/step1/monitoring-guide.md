# Step 1 모니터링 가이드

## 개요

각 Phase별로 필요한 메트릭을 모니터링하기 위한 가이드입니다. Grafana 대시보드에서 Phase 변수를 선택하여 각 Phase별 성능 지표를 확인할 수 있습니다.

## Grafana 대시보드 접속

- **URL**: `http://localhost:3000`
- **로그인**: admin / admin
- **대시보드**: "Step 1: Phase별 성능 비교" 선택

## Phase 변수 사용 방법

대시보드 상단의 **Phase** 드롭다운에서 현재 실행 중인 Phase를 선택합니다:

- **Phase 1**: 베이스라인 (캐시 OFF + 인덱스 OFF)
- **Phase 2**: 인덱스 적용 (캐시 OFF + 인덱스 ON)
- **Phase 3**: 캐시 적용 (캐시 ON + 인덱스 ON)

Phase를 선택하면 해당 Phase에 맞는 메트릭이 표시됩니다.

---

## Phase 1: 베이스라인 모니터링

### 확인 항목

#### HTTP 요청 메트릭
- **RPS**: 초당 요청 수
- **응답 시간**: p50, p95, p99
- **응답 코드 분포**: 200, 302, 404 비율
- **에러율**: 5xx 에러 비율

#### 데이터베이스 메트릭
- **DB QPS**: 초당 쿼리 수
- **Active connections**: 활성 커넥션 수
- **DB CPU**: CPU 사용률 (가능한 경우)

#### 애플리케이션 메트릭
- **CPU 사용률**: 애플리케이션 CPU
- **메모리 사용률**: JVM Heap 메모리
- **GC 통계**: GC 시간 및 횟수

### 체크리스트

- [ ] RPS 확인 (목표 대비)
- [ ] p95 latency 확인 (기준선 기록)
- [ ] DB QPS 확인 (기준선 기록)
- [ ] DB Active connections 확인
- [ ] 에러율 확인 (< 1% 목표)

---

## Phase 2: 인덱스 적용 모니터링

### 확인 항목

Phase 1과 동일한 항목 + 추가 확인:

#### 인덱스 효과 확인
- **응답 시간 개선**: Phase 1 대비 p95, p99 latency 감소
- **DB QPS 변화**: Phase 1 대비 변화량
- **DB CPU 감소**: Phase 1 대비 CPU 사용률 감소

### 체크리스트

- [ ] Phase 1과 동일한 메트릭 확인
- [ ] p95 latency Phase 1 대비 개선율 확인
- [ ] DB QPS Phase 1 대비 변화 확인
- [ ] DB CPU Phase 1 대비 감소 확인
- [ ] EXPLAIN ANALYZE 결과 확인 (Index Scan 전환)

---

## Phase 3: 캐시 적용 모니터링

### 확인 항목

Phase 2와 동일한 항목 + 추가 확인:

#### 캐시 메트릭
- **캐시 히트율**: 실시간 캐시 히트율 (목표: 80%+)
- **Redis 메모리**: Redis 메모리 사용량
- **Redis CPU**: Redis CPU 사용률

#### DB 부하 감소 확인
- **DB QPS 감소**: Phase 2 대비 감소량 (목표: 70%+ 감소)
- **DB CPU 감소**: Phase 2 대비 감소량
- **Active connections 감소**: Phase 2 대비 감소량

### 체크리스트

- [ ] Phase 2와 동일한 메트릭 확인
- [ ] 캐시 히트율 확인 (목표: 80%+)
- [ ] DB QPS Phase 2 대비 감소 확인 (목표: 70%+ 감소)
- [ ] p50 latency 확인 (캐시 히트 시 크게 개선)
- [ ] Redis 메모리/CPU 확인

---

## 주요 메트릭 확인 방법

### HTTP 응답 시간

**대시보드 패널**: "HTTP 응답 시간 (P95, P50, P99)"

**확인 방법**:
- p95: 95% 요청이 이 시간 이내에 처리됨
- p50: 중간값 (평균보다 정확)
- p99: 99% 요청이 이 시간 이내에 처리됨

### RPS (Requests Per Second)

**대시보드 패널**: "HTTP 요청 처리율"

**확인 방법**:
- 초당 요청 수를 실시간으로 확인
- 부하 테스트 중 증가 추이 확인

### 캐시 히트율 (Phase 3만)

**대시보드 패널**: "Redis 캐시 히트/미스"

**또는 API 호출**:
```bash
curl http://localhost:8080/admin/cache/stats
```

**확인 방법**:
- 히트율 = hits / (hits + misses) * 100
- 목표: 80% 이상

### DB QPS

**대시보드 패널**: "데이터베이스 QPS" (있는 경우)

**또는 PostgreSQL 직접 조회**:
```sql
SELECT count(*) FROM pg_stat_activity WHERE state = 'active';
```

---

## 모니터링 시점

### k6 테스트 실행 전
- [ ] 대시보드 접속 확인
- [ ] Phase 변수 선택 확인
- [ ] 메트릭 수집 확인

### k6 테스트 실행 중
- [ ] 실시간 메트릭 모니터링
- [ ] 이상 지표 확인 (에러율 급증, latency 급증 등)
- [ ] 리소스 사용률 확인 (CPU, 메모리)

### k6 테스트 실행 후
- [ ] 최종 메트릭 값 기록
- [ ] Phase별 비교표 작성
- [ ] 이상 지표 분석

---

## 문제 해결

### 메트릭이 표시되지 않는 경우

1. **Prometheus 연결 확인**
   - Prometheus 접속: `http://localhost:9090`
   - Targets 확인: `http://localhost:9090/targets`
   - `bitly-api` target이 UP 상태인지 확인

2. **애플리케이션 Actuator 확인**
   - Health: `http://localhost:8081/actuator/health`
   - Metrics: `http://localhost:8081/actuator/metrics`
   - Prometheus: `http://localhost:8081/actuator/prometheus`

3. **Grafana 데이터소스 확인**
   - Grafana → Configuration → Data Sources
   - Prometheus 데이터소스 연결 확인

### 캐시 히트율이 0%인 경우 (Phase 3)

- 캐시가 활성화되었는지 확인
- Redis 연결 확인
- 테스트 데이터가 충분한지 확인 (Hot/Warm 분포)

---

## 참고 자료

- [Phase 1: 베이스라인 측정](./phase1-baseline.md)
- [Phase 2: 인덱스 적용](./phase2-index.md)
- [Phase 3: 캐시 적용](./phase3-cache.md)
- [종합 비교 분석](./comparison.md)
