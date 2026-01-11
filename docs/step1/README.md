# Step 1: URL 조회 성능 최적화 전략 - 단계별 비교 분석

> 목표: **각 최적화 전략(인덱스, 캐시)의 효과를 정량적으로 비교 분석**하여 각 전략이 성능에 미치는 영향을 명확히 파악한다.  
> 범위: "단축 URL 조회(`GET /{shortCode}`) → 리다이렉트 응답" 중심.  
> 접근: **변수 제어 실험** - 각 Phase마다 하나의 변수만 변경하여 그 효과를 측정.

---

## 용어 및 전제

- **VU**: k6 Virtual User(가상 사용자)
- **RPS**: 초당 요청 수(Requests per second)
- **EXPLAIN ANALYZE**: PostgreSQL 쿼리 실행 계획 및 실제 실행 통계
- **병목 분석**: 앱/DB/캐시/네트워크/커넥션풀 등 어느 구간이 한계인지 "근거 로그/메트릭"으로 확인

---

## 현재 서비스 상태

### 조회 API 구조

- **엔드포인트**: `GET /{shortCode}`
- **컨트롤러**: `UrlController.redirect()`
- **서비스**: `UrlService.getOriginalUrl()` → `UrlCacheService.getOriginalUrl()`
- **Repository**: `UrlRepository.findByShortUrl(String shortUrl)`

### 현재 구현 상태

- **인덱스**: `idx_url_short_url` on `shortUrl` column (이미 존재)
- **캐시**: Redis Cache-Aside 패턴 구현됨
  - 캐시 키: `url:{shortCode}`
  - TTL: 3600초 (1시간)
  - 캐시 히트율 통계 수집 중

### 실험 설계 원칙

- **변수 제어**: 각 Phase마다 **하나의 변수만** 변경
- **동일 조건 유지**: 데이터/시나리오/인프라 조건 동일
- **정량적 비교**: 전/후 결과를 수치로 비교 가능하게 측정

---

## 테스트 시나리오

### 핵심 경로

- **입력**: `GET /{shortCode}`
- **서버 동작**:
  1. shortCode 유효성 체크
  2. (Phase에 따라) 캐시 또는 DB에서 원본 URL 조회
  3. 302 Found로 Location 헤더 반환

### 데이터 분포 (현실 반영)

- **Hot set**: 상위 1% 코드가 전체 트래픽의 50%
- **Warm set**: 상위 10% 코드가 전체 트래픽의 80%
- **Cold set**: 나머지 90% 코드가 20%
- **404 케이스**: 존재하지 않는 코드 1~5%

> 이 분포를 사용하면 캐시 히트율과 인덱스 효과가 실제처럼 드러남.

### k6 테스트 시나리오

- **스모크 테스트**: VU 10~50, 1~3분 (정상 동작 확인)
- **램프업**: VU 50 → 500 → 1,000 (각 5분 유지)
- **스테디 상태**: VU 1,000 유지 10분 (안정화 확인)

---

## 모니터링

**Grafana 대시보드**: `http://localhost:3000` → "Step 1: Phase별 성능 비교"

대시보드 상단의 **Phase** 변수로 현재 실행 중인 Phase를 선택하여 모니터링합니다.

**상세 가이드**: [monitoring-guide.md](./monitoring-guide.md)

---

## Phase 구조

### Phase 1: 베이스라인 측정

- **상태**: 캐시 OFF + 인덱스 OFF
- **목적**: 최적화 전 상태에서 병목 위치 파악
- **문서**: [phase1-baseline.md](./phase1-baseline.md)

### Phase 2: 인덱스 적용

- **상태**: 캐시 OFF + 인덱스 ON
- **목적**: 인덱스만 추가하여 DB 조회 성능 개선 효과 측정
- **문서**: [phase2-index.md](./phase2-index.md)

### Phase 3: 캐시 적용

- **상태**: 캐시 ON + 인덱스 ON
- **목적**: 캐시를 추가하여 Hot/Warm 트래픽을 Redis로 흡수
- **문서**: [phase3-cache.md](./phase3-cache.md)

### 종합 비교 분석

- **문서**: [comparison.md](./comparison.md)

---

## 테스트 데이터셋 준비

각 Phase에서 동일한 조건으로 테스트하기 위해 데이터셋을 미리 생성합니다.

**가이드**: [data-preparation.md](./data-preparation.md)

**빠른 시작**:

```bash
cd k6-tests/test-data
python generate-dataset.py --count 1000
```

---

## 다음 단계

1. **Phase 1 실행**: 베이스라인 측정 및 병목 확인 → [phase1-baseline.md](./phase1-baseline.md)
2. **Phase 2 실행**: 인덱스 효과 측정 → [phase2-index.md](./phase2-index.md)
3. **Phase 3 실행**: 캐시 효과 측정 → [phase3-cache.md](./phase3-cache.md)
4. **종합 분석**: 3단계 비교표 작성 및 결론 도출 → [comparison.md](./comparison.md)
