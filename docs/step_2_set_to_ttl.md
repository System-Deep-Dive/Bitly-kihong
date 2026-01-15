# Bitly TTL 전략 선택을 위한 실험 가이드

> 목적: 단축 URL 조회 트래픽(Bitly 류)에서 **TTL(Time To Live) 전략을 ‘감’이 아닌 실험 결과로 선택**한다.  
> 핵심 질문: “어떤 TTL 전략이 VU 10,000에서도 p99 안정성과 DB 보호를 동시에 만족시키는가?”

---

## 1. 왜 TTL을 실험으로 결정해야 하나?

TTL은 단순한 캐시 옵션이 아니라 **성능과 안정성을 동시에 흔드는 레버**다.

- TTL이 너무 짧으면 → 캐시 미스 폭주 → DB 스파이크
- TTL이 너무 길면 → 정합성/운영 리스크 증가
- 동일 TTL이면 → 동시 만료(Cache Stampede) 발생

➡️ 따라서 **TTL 숫자 선택은 반드시 실험 기반**이어야 한다.

---

## 2. 실험의 전제 조건 (고정 변수)

TTL 실험의 신뢰도를 위해 아래 조건은 반드시 고정한다.

- 동일 인프라 스펙 (App / DB / Redis)
- 동일 데이터 분포
  - Hot 1% → 50~70%
  - Warm 9%
  - Cold 90% → 20~30%
- 동일 k6 시나리오
- 동일 SLO 기준

---

## 3. TTL 전략 후보군 (실험 단위)

TTL은 **숫자 단독이 아니라 전략 단위로 비교**한다.

### 실험군 A. 고정 TTL (Baseline)
```text
TTL = 5m
jitter = 없음
```
- 목적: Cache Stampede가 실제로 얼마나 위험한지 확인
- 예상: TTL 만료 시 p99, DB QPS spike 발생

---

### 실험군 B. 고정 TTL + Jitter (권장 기본형)
```text
TTL = 5m ± 20%
```
- 목적: 만료 분산 효과 검증
- 예상: A 대비 p99 안정화, DB spike 감소

---

### 실험군 C. 긴 TTL + Jitter (성능 극대화)
```text
TTL = 1h ± 20%
```
- 목적: DB 부하 최소화
- 리스크: 비활성/만료 반영 지연

---

### 실험군 D. 상태 기반 TTL (운영형)
| 링크 상태 | TTL 전략 |
|---------|---------|
| 영구 링크 | 1h ± 20% |
| 만료 예정 | min(expires_at-now, 10m) |
| 비활성/삭제 | 30s |
| 없는 코드 | 30s (negative cache) |

- 목적: 성능 + 정합성 균형
- 실서비스에 가장 가까운 형태

---

### (선택) 실험군 E. Soft TTL + Background Refresh
- Hard TTL: 1h
- Soft TTL: 5m
- Soft TTL 초과 시:
  - 응답은 캐시 사용
  - 백그라운드에서 갱신

> 구현 난이도는 높지만, 대규모 트래픽에서 p99 안정성 최상

---

## 4. TTL 실험용 부하 시나리오 (k6)

### 4.1 Steady Load
- VU 2,000
- Duration: 10분
- 목적: 기본 latency, hit rate 측정

---

### 4.2 Burst Load (Bitly 현실 반영)
- 0 → 10,000 VU (5분 ramp-up)
- 10,000 유지 10분
- 10,000 → 0
- 목적: 급격한 유입 시 캐시/DB 거동 확인

---

### 4.3 TTL Expiry Spike 재현 (핵심)
- Hot key 비중: 70%
- 테스트 시간: TTL이 최소 2~3회 만료되도록 설정
- 관측 포인트:
  - TTL 만료 직후 p99 변화
  - DB QPS peak

---

## 5. 필수 관측 지표

### 5.1 Redis
- hit rate (%)
- expired_keys/sec
- evicted_keys
- CPU / memory / network

### 5.2 DB
- QPS
- active connections
- lock/wait
- slow query
- TTL 만료 시점의 spike 여부

### 5.3 Application
- p50 / p95 / p99 latency
- error rate / timeout
- cache miss → DB fallback 비율

---

## 6. TTL 전략 선택 기준 (Decision Rule)

TTL 전략은 아래 조건을 만족해야 “합격”이다.

- VU 10,000에서
  - p99 < 150ms
  - error rate < 0.1%
- TTL 만료 구간에서도
  - p99 급격한 스파이크 없음
  - DB QPS가 안전 한계 내
- Redis 메모리/eviction 안정

➡️ 이 조건을 **가장 단순한 전략으로 만족하는 TTL**을 선택한다.

---

## 7. 실무에서 자주 나오는 결과 패턴

- ❌ 고정 TTL (no jitter)
  - TTL 만료 시 p99 폭발 → 탈락
- ✅ 고정 TTL + jitter
  - 대부분 서비스에서 기본 해법
- ⚠️ 긴 TTL + jitter
  - 성능은 좋지만 invalidate/상태 관리 필요
- ✅ 상태 기반 TTL
  - 운영 난이도 ↑, 안정성 ↑

---

## 8. 권장 시작점 (실전 기준)

1. TTL = 5m ± 20% (jitter)
2. negative cache = 30s
3. VU 10,000 TTL expiry 테스트 실행
4. DB가 여전히 병목이면 TTL 상향
5. 정합성 이슈 발생 시 상태 기반 TTL로 진화

---

## 9. 문서화 포인트 (블로그/리뷰용)

- “TTL을 몇 분으로 정했다” ❌  
- “TTL 후보 4가지를 실험했고, p99/DB spike 기준으로 선택했다” ✅  

➡️ 이 한 줄이 설계 신뢰도를 완전히 바꾼다.

---

## 10. 다음 확장 실험
- singleflight / request coalescing 효과 비교
- Redis Cluster vs 단일 노드
- Soft TTL vs Hard TTL 비교
- 캐시 미스 시 DB fallback rate 제한 실험
