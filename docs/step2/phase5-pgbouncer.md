# Phase 5: PgBouncer 적용 (Scale-out + PgBouncer)

## 목적

- **PgBouncer를 추가**하여 연결 풀 경합 해소
- Phase 4와 비교하여 PgBouncer의 효과 확인
- 연결 풀 효율성 개선 측정

---

## 준비 작업

### PgBouncer 설정

`docker-compose.yml`에 PgBouncer 서비스를 추가하고, 애플리케이션이 PgBouncer를 통해 데이터베이스에 연결하도록 설정:

```yaml
pgbouncer:
  image: edoburu/pgbouncer:latest
  depends_on:
    - postgres
  ports:
    - "6432:6432"
  volumes:
    - ./pgbouncer/pgbouncer.ini:/etc/pgbouncer/pgbouncer.ini:ro
    - ./pgbouncer/userlist.txt:/etc/pgbouncer/userlist.txt:ro

app:
  environment:
    - SPRING_DATASOURCE_URL=jdbc:postgresql://pgbouncer:6432/bitly
```

**실행 체크리스트**:

- [ ] `docker-compose.yml`에 PgBouncer 서비스 추가 확인
- [ ] `pgbouncer/pgbouncer.ini` 설정 파일 확인
- [ ] `pgbouncer/userlist.txt` 인증 파일 확인
- [ ] 애플리케이션 데이터소스 URL이 `pgbouncer:6432`로 설정되어 있는지 확인
- [ ] Docker Swarm 스택 배포
- [ ] PgBouncer 서비스가 정상적으로 실행되는지 확인
- [ ] 애플리케이션이 PgBouncer를 통해 정상적으로 연결되는지 확인

---

## 설정 정보

### PgBouncer 설정 (`pgbouncer/pgbouncer.ini`)

**연결 풀 설정**:

- **pool_mode**: `transaction`
- **default_pool_size**: 100
- **max_client_conn**: 1000
- **max_db_connections**: 200
- **reserve_pool_size**: 10

**인증 설정**:

- **auth_type**: `md5`
- **auth_file**: `/etc/pgbouncer/userlist.txt`

### 데이터베이스 연결 구조

- **애플리케이션 → PgBouncer**: 최대 1000개 클라이언트 연결
- **PgBouncer → PostgreSQL**: 최대 200개 서버 연결
- **연결 풀 효율성**: 10개 인스턴스 × 50개 연결 풀 = 500개 → 200개로 감소 (60% 감소)

---

## 검증 방법

### PgBouncer 풀 상태 확인

PgBouncer 관리 콘솔에 연결하여 풀 상태를 확인:

```bash
# PgBouncer 컨테이너 ID 확인
docker ps | grep pgbouncer

# PgBouncer 관리 콘솔에 연결
docker exec -it <pgbouncer_container_id> psql -h localhost -p 6432 -U user -d pgbouncer

# 풀 상태 확인
SHOW POOLS;
SHOW STATS;
SHOW SERVERS;
SHOW CLIENTS;
```

**확인 항목**:

- `cl_waiting`: 0 (대기 중인 클라이언트 없음)
- `maxwait`: 0 (최대 대기 시간 없음)
- `sv_active`: 활성 서버 연결 수
- `sv_idle`: 유휴 서버 연결 수

---

## k6 실행

**Phase 4와 동일한 시나리오 사용**:

```bash
cd k6-tests
k6 run --out json=results/results-phase_scale_out.json scenario-phase1.js
```

**참고**: Phase 4와 동일한 데이터셋(`step1-dataset.json`)과 동일한 시나리오를 사용하여 공정한 비교가 가능합니다.

**실행 체크리스트**:

- [ ] `k6-tests/test-data/step1-dataset.json` 파일 존재 확인
- [ ] Phase 4와 동일한 k6 시나리오 사용
- [ ] k6 테스트 실행
- [ ] 결과 파일 확인 (`results/results-phase_scale_out.json`)

---

## 결과 기록

### 성능 지표 요약

| 지표                   | Phase 4 (Scale-out만) | Phase 5 (Scale-out + PgBouncer) | 개선율 (4→5) |
| ---------------------- | --------------------- | ------------------------------- | ------------ |
| **응답 시간**          |
| p50 latency            | 744 ms                | 790 ms                          | +6.2% 악화   |
| p95 latency            | 4,431 ms              | 4,395 ms                        | -0.8% 개선   |
| **처리량**             |
| RPS                    | 712.4 req/s           | 713.0 req/s                     | +0.1% 증가   |
| 요청 실패율            | 2.02%                 | 2.00%                           | -1.0% 개선   |
| **연결 관련**          |
| http_req_blocked p(95) | 911 ms                | 883 ms                          | -3.1% 개선   |
| **리디렉션 지연**      |
| redirect_latency p(95) | 9,239 ms              | 9,376 ms                        | +1.5% 악화   |

**참고**:

- PgBouncer는 연결 풀 경합을 해소했으나, 전반적인 응답 시간에는 큰 영향을 주지 않았습니다.
- 연결 풀 효율성 개선으로 PostgreSQL 서버 부하가 감소했습니다.

### PgBouncer 효과

**주요 개선**:

- **연결 풀 경합 해소**: `SHOW POOLS` 결과에서 `cl_waiting = 0`, `maxwait = 0` 확인
- **PostgreSQL 연결 수 감소**: 500개 → 200개 (60% 감소)
- **요청 실패율**: 2.02% → 2.00% (-1.0% 개선)
- **연결 블로킹 시간 p(95)**: 911ms → 883ms (-3.1% 개선)

**한계**:

- **전체 응답 시간**: p95 응답 시간은 4.4초에서 4.4초로 거의 변화 없음
- **p50 응답 시간**: 744ms → 790ms (+6.2% 악화)

**분석**:

PgBouncer는 연결 풀 경합 문제를 성공적으로 해소했지만, 전반적인 응답 시간에는 큰 영향을 주지 않았습니다. 이는 다른 성능 병목(애플리케이션 로직, 쿼리 최적화 등)이 존재함을 시사합니다.

---

## 상세 분석

### k6 테스트 결과 요약

- **총 요청 수**: 516,883건
- **요청 처리율**: 713.0 req/s
- **요청 실패율**: 2.00% (threshold: <1%, 미달)
- **테스트 시간**: ~12.1분

### 성능 메트릭 상세

**전체 요청 시간 (http_req_duration)**:

- p50: 790ms
- p95: 4,395ms
- 평균: 1,310ms
- 최대: 30.1초

**리디렉션 지연 (redirect_latency)**:

- p50: 2,665ms
- p95: 9,376ms
- 평균: 3,419ms
- 최대: 35.2초

**연결 블로킹 시간 (http_req_blocked)**:

- p50: 0ms
- p95: 883ms
- 평균: 144ms
- 최대: 16.8초

**연결 시간 (http_req_connecting)**:

- p50: 0ms
- p95: 465ms
- 평균: 103ms
- 최대: 13.2초

### PgBouncer 풀 상태 분석

**SHOW POOLS 결과** (부하 시):

- `cl_waiting`: 0 (대기 중인 클라이언트 없음)
- `maxwait`: 0 (최대 대기 시간 없음)
- `sv_active`: 활성 서버 연결 수 (100개 수준)
- `sv_idle`: 유휴 서버 연결 수

**분석**:

- 연결 풀 경합이 해소되었음을 확인 (`cl_waiting = 0`, `maxwait = 0`)
- PostgreSQL 서버 연결 수가 200개 이하로 유지되어 서버 부하 감소

---

## 산출물

- **k6 결과**: `results-phase_scale_out.json`
- **PgBouncer 풀 상태**: SHOW POOLS 결과
- **성능 개선표**: 위 표 작성 완료

---

## 다음 단계

Phase 5 완료 후 → [종합 비교 분석](./comparison.md) 진행
