# k6 성능 테스트

## 개요

Redis 기반 URL Counter 및 Cache System의 성능을 검증하기 위한 k6 부하 테스트입니다.

## 통합된 아키텍처

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   k6 Tests      │    │   Application   │    │   Monitoring    │
│                 │    │                 │    │                 │
│ - URL Generator │───▶│ - Spring Boot   │───▶│ - Prometheus    │
│ - Load Test     │    │ - Redis Cache   │    │ - Grafana       │
│ - Redis Monitor │    │ - PostgreSQL    │    │ - InfluxDB      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────┐
                    │   Docker        │
                    │   Compose       │
                    │                 │
                    │ - PostgreSQL    │
                    │ - Redis (6380)  │
                    │ - Prometheus    │
                    │ - Grafana       │
                    │ - InfluxDB      │
                    │ - Node Exporter │
                    │ - Redis Exporter│
                    └─────────────────┘
```

## 시나리오

### 시나리오 B: 캐시 성능 테스트 ✅ 완료

**목표**: Redis 기반 캐시 시스템의 성능 검증

#### 🎯 실제 테스트 결과

| 지표             | 목표    | 실제 결과    | 상태    |
| ---------------- | ------- | ------------ | ------- |
| **에러율**       | < 1%    | 0.00%        | ✅ 달성 |
| **TPS**          | > 1,000 | 10,597 req/s | ✅ 달성 |
| **P95 응답시간** | < 100ms | 148ms        | ⚠️ 초과 |
| **총 요청**      | -       | 3,189,212개  | ✅ 안정 |

#### 테스트 시나리오

- **API**: `GET /{shortCode}` (리다이렉션)
- **부하**: 1,000 VU 고정, 5분간 테스트
- **패턴**: 80% 인기키 / 20% 비인기키 요청
- **URL 생성**: 테스트 시작 시 50개의 실제 URL 동적 생성

## 빠른 시작

### 1. 전체 환경 시작

```bash
# 모든 서비스 시작 (PostgreSQL, Redis, Prometheus, Grafana, InfluxDB)
docker-compose up -d
```

### 2. 시나리오 B 실행

```bash
cd k6-tests

# 권장: JSON 출력으로 실행
k6 run --out json=results.json scenario-b-fast.js

# 또는 InfluxDB 연동 (선택사항)
k6 run --out influxdb=http://localhost:8086/k6 scenario-b-fast.js
```

## 모니터링

### 대시보드 접근

- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **InfluxDB**: http://localhost:8086

### 포트 정보

- **8080**: Spring Boot 애플리케이션
- **8081**: Spring Boot Actuator
- **3000**: Grafana
- **9090**: Prometheus
- **8086**: InfluxDB
- **6380**: Redis ⚠️ (포트 변경됨)
- **5432**: PostgreSQL

## 성능 목표 vs 실제 결과

| 지표          | 목표치  | 실제 결과 | 상태 | 측정 방법            |
| ------------- | ------- | --------- | ---- | -------------------- |
| P95 응답시간  | < 100ms | 148ms     | ⚠️   | k6 http_req_duration |
| 에러율        | < 1%    | 0.00%     | ✅   | k6 http_req_failed   |
| TPS           | > 1000  | 10,597    | ✅   | k6 http_reqs         |
| 메모리 사용량 | < 1GB   | -         | -    | Redis memory         |
| CPU 사용률    | < 80%   | -         | -    | Node Exporter        |

## 해결된 주요 문제

### 1. 높은 에러율 (95.81%) → 0.00%

**원인**: 하드코딩된 가상 shortCode 사용  
**해결**: 실제 존재하는 URL을 동적으로 생성

### 2. InfluxDB "Request Entity Too Large" 에러

**원인**: k6에서 InfluxDB로 전송하는 배치 데이터가 너무 큼  
**해결**: JSON 출력 사용 권장

### 3. Redis 포트 충돌

**원인**: 포트 6379가 다른 프로세스에 의해 사용됨  
**해결**: Redis 포트를 6380으로 변경

## 상세 가이드

- **시나리오 B**: [SCENARIO_B.md](./SCENARIO_B.md) - 시나리오 B 상세 실행 가이드
