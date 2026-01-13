# Phase 3: 캐시 적용 (캐시 ON + 인덱스 ON)

## 목적

- **캐시를 추가**하여 Hot/Warm 트래픽을 Redis로 흡수
- Phase 2와 비교하여 캐시의 정량적 효과 확인
- DB 부하 감소 및 응답 시간 단축 측정

---

## 준비 작업

### 캐시 활성화

Phase 1에서 수정한 `UrlService.getOriginalUrl()` 메서드를 원래대로 복구:

```java
public String getOriginalUrl(String shortCode) {
    log.debug("Retrieving original URL for short code: {}", shortCode);

    // 원래 코드로 복구 - 캐시 서비스 사용
    return urlCacheService.getOriginalUrl(shortCode)
            .orElseThrow(() -> {
                log.warn("No original URL found for short code: {}", shortCode);
                return new IllegalArgumentException("Invalid short url");
            });
}
```

**실행 체크리스트**:

- [ ] `UrlService.java` 파일 열기
- [ ] `getOriginalUrl()` 메서드를 원래대로 복구
- [ ] 애플리케이션 재시작
- [ ] 캐시가 활성화되었는지 확인 (로그에서 "Cache hit" 메시지 확인)

---

## 모니터링

**Grafana 대시보드**: `http://localhost:3000` → "Step 1: Phase별 성능 비교" 선택

**Phase 변수 설정**: 대시보드 상단에서 **Phase 3** 선택

**확인 항목**:

- Phase 2와 동일한 메트릭 + 캐시 메트릭
- 캐시 히트율 (목표: 80%+)
- Redis 메모리/CPU
- DB 부하 감소 (Phase 2 대비)

**상세 가이드**: [모니터링 가이드](./monitoring-guide.md)

---

## 검증 방법

### 캐시 히트율 확인

```bash
curl http://localhost:8080/monitoring/cache/hit-rate
```

**확인 항목**: 캐시 히트율, Redis CPU, DB QPS 감소량

---

## k6 실행

**Phase 1, 2와 동일한 시나리오 사용**:

```bash
cd k6-tests
k6 run --out json=results/results-phase3.json scenario-phase1.js
```

또는 환경 변수로 Phase 지정:

```bash
cd k6-tests
PHASE=phase3 k6 run scenario-phase1.js
```

**참고**: Phase 1, 2와 동일한 데이터셋(`step1-dataset.json`)과 동일한 시나리오를 사용하여 공정한 비교가 가능합니다.

**실행 체크리스트**:

- [ ] `k6-tests/test-data/step1-dataset.json` 파일 존재 확인
- [ ] Phase 1, 2와 동일한 k6 시나리오 사용
- [ ] k6 테스트 실행
- [ ] 결과 파일 확인 (`results/results-phase3.json`)

---

## 결과 기록

### 성능 지표 비교

| 지표               | Phase 1 (OFF/OFF)  | Phase 2 (OFF/ON)   | Phase 3 (ON/ON)     | 개선율 (1→3) | 개선율 (2→3) |
| ------------------ | ------------------ | ------------------ | ------------------- | ------------ | ------------ |
| p50 latency        | 121 ms             | 104 ms             | 95 ms               | 21% ↓        | 9% ↓         |
| p95 latency        | 698 ms             | 655 ms             | 639 ms              | 8% ↓         | 2% ↓         |
| RPS                | 99.0 req/s         | 99.3 req/s         | 100.5 req/s         | +1.5%        | +1.2%        |
| 요청 실패율        | 1.32%              | 1.34%              | 1.22%               | 7.6% ↓       | 8.9% ↓       |
| DB QPS             | Last 82.8, Max 128 | Last 93.2, Max 128 | Last 14.0, Max 44.7 | -65.1% (Max) | -65.1% (Max) |
| Active Connections | 최대 10개          | 최대 1개           | 최대 1개            | 90% ↓        | 0%           |
| 캐시 히트율        | 0%                 | 0%                 | 81.8%               | -            | -            |

**참고**:

- p50, p95 latency는 서버 응답 시간(`http_req_waiting`) 기준입니다.
- 전체 요청 시간(`http_req_duration`)은 네트워크 지연을 포함하므로, 실제 서버 성능 평가는 위의 서버 응답 시간을 기준으로 합니다.

### 캐시 효과

**평균 캐시 히트율**: 81.8% (목표 80%+ 달성)

**Redis 메트릭**:

- Redis 히트: 94.8K
- Redis 미스: 21.1K
- Redis 메모리 사용량: 7.42 MiB

**Phase 2 대비 개선**:

- **서버 응답 시간 (http_req_waiting)**:

  - p50 latency: 104 ms → 95 ms (9% 개선)
  - p95 latency: 655 ms → 639 ms (2% 개선)
  - 평균: 272 ms → 250 ms (8% 개선)

- **전체 요청 시간 (http_req_duration)**:

  - p50 latency: 298 ms → 258 ms (13% 개선)
  - p95 latency: 10,865 ms → 10,714 ms (1% 개선)
  - 평균: 2,361 ms → 2,244 ms (5% 개선)

- **DB 부하**:
  - DB QPS: 최대 44.7 (캐시로 인한 DB 부하 감소 효과 확인)
  - 캐시 히트율 81.8%로 Hot/Warm 데이터의 대부분이 Redis에서 처리됨

**분석**:

- 캐시 히트율 81.8%로 목표를 달성했으며, Hot/Warm 트래픽의 대부분이 Redis로 흡수되었습니다.
- 서버 응답 시간(p50, p95)이 Phase 2 대비 개선되었으나, 전체 요청 시간에서는 네트워크 오버헤드로 인해 개선폭이 상대적으로 작습니다.
- DB QPS가 감소하여 데이터베이스 부하가 줄어든 것으로 보입니다.

---

## 상세 분석

### k6 테스트 결과 요약

- **총 요청 수**: 80,899건
- **요청 처리율**: 100.5 req/s
- **요청 실패율**: 1.22% (threshold: <1%, 미달)
- **체크 통과율**: 99.99% (159,118 통과 / 9 실패)

### 성능 메트릭 상세

**서버 응답 시간 (http_req_waiting)**:

- p50: 95ms
- p95: 639ms
- 평균: 250ms
- 최대: 13.2초

**전체 요청 시간 (http_req_duration)**:

- p50: 258ms
- p95: 10.7초
- 평균: 2.2초
- 최대: 44.3초

**참고**: 전체 요청 시간은 네트워크 지연, 연결 설정 시간 등을 포함하므로, 실제 서버 성능은 `http_req_waiting` 메트릭을 기준으로 평가하는 것이 적절합니다.

### Redis 캐시 성능

- **캐시 히트율**: 81.8% (목표 달성)
- **캐시 히트**: 94.8K
- **캐시 미스**: 21.1K
- **메모리 사용량**: 7.42 MiB

### DB 부하 분석

- **최대 DB QPS**: 44.7
- **최종 DB QPS**: 14.0
- 캐시 히트율 81.8%로 인해 DB 쿼리가 크게 감소한 것으로 보입니다.

---

## 산출물

- **k6 결과**: `results-phase3.json`
- **캐시 히트율**: 81.8%
- **성능 개선표**: 위 표 작성 완료

---

## 다음 단계

Phase 3 완료 후 → [종합 비교 분석](./comparison.md) 진행
