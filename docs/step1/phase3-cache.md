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

| 지표        | Phase 1 (OFF/OFF) | Phase 2 (OFF/ON) | Phase 3 (ON/ON) | 개선율 (1→3) | 개선율 (2→3) |
| ----------- | ----------------- | ---------------- | --------------- | ------------ | ------------ |
| p50 latency | ? ms              | ? ms             | ? ms            | ?%           | ?%           |
| p95 latency | ? ms              | ? ms             | ? ms            | ?%           | ?%           |
| p99 latency | ? ms              | ? ms             | ? ms            | ?%           | ?%           |
| DB QPS      | ?                 | ?                | ?               | ?%           | ?%           |
| DB CPU      | ?%                | ?%               | ?%              | ?%           | ?%           |
| 캐시 히트율 | 0%                | 0%               | ?%              | -            | -            |
| Redis CPU   | -                 | -                | ?%              | -            | -            |

### 캐시 효과

**평균 캐시 히트율**: ?%

**Phase 2 대비 개선**:

- p95 latency: ? ms → ? ms (?% 개선)
- p50 latency: ? ms → ? ms (?% 개선)
- DB QPS: ? → ? (?% 감소)
- DB CPU: ?% → ?% (?% 감소)

---

## 산출물

- **k6 결과**: `results-phase3.json`
- **캐시 히트율**: 위 표 작성
- **성능 개선표**: 위 표 작성

---

## 다음 단계

Phase 3 완료 후 → [종합 비교 분석](./comparison.md) 진행
