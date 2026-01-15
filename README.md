# Bitly-Kihong

## 1. 문서의 기본 정보 (Title and People)

제목: URL 리다이렉션을 빠르게 처리하기  
작성자: 정기홍
최종 업데이트 날짜: 2025-10-04

## 2. 개요 (Overview)

대규모 URL 단축 서비스인 Bitly의 시스템 중 URL 단축, 리다이렉션을 구현하고 성능을 최적화하는 프로젝트입니다.  
Redis 기반 인메모리 캐시와 PostgreSQL 데이터베이스를 활용하여 고성능 URL 리다이렉션 시스템을 구축했습니다.

### 🚀 현재 구현 상태

- ✅ **Redis 인메모리 캐시** - Cache-Aside 패턴으로 응답 시간 최적화
- ✅ **Redis 전역 카운터** - 분산 환경에서 고유 ID 보장
- ✅ **Base62 인코딩** - URL-safe한 짧은 단축 코드 생성
- ✅ **PostgreSQL 데이터베이스** - 영구 저장소 및 인덱싱
- ✅ **Spring Boot 3.5.5 + Java 21** - 최신 기술 스택
- ✅ **포괄적인 테스트** - 단위/통합/성능 테스트 완료
- ✅ **Docker 환경** - PostgreSQL과 Redis 컨테이너화
- ✅ **k6 부하 테스트** - 대규모 트래픽 성능 검증 완료

## 3. 배경 및 문제 정의 (Context)

- 문제 설명: 현재 시스템은 모든 URL 데이터를 단일 데이터베이스에 저장하고 있으며, 리다이렉션 요청시 단축 코드를 찾는 과정에서 전체 테이블 스캔이 발생합니다. URL 수가 증가하면서 탐색 시간이 기하급수적으로 늘어나고 있습니다.
- 필요성: 리다이렉션 속도는 사용자 경험과 서비스 안정에 직결되는 핵심 지표입니다. 트래픽이 집중되는 피크 타임에서 발생하는 지연 시간은 사용자 이탈을 야기할 수 있으므로, 대규모 트래픽을 효율적으로 처리할 수 있어야 합니다.

## 4. 목표와 비목표 (Goals and Non-Goals)

목표 (Goals):

- 사용자 중심의 영향: 사용자가 단축 URL 클릭 시 1초 미만의 지연 시간으로 즉시 리다이렉션될 수 있도록 합니다.
- 성공 측정 지표(KPI):
  - 평균 리다이렉션 응답 시간을 200ms 미만으로 단축
  - 최대 트래픽 발생 시에도 99.9%의 요청 성공률 유지

비목표 (Non-Goals):

- URL 생성 기능의 성능 최적화는 포함되지 않습니다.
- 사용자 대시보드와 같은 복잡한 부가 기능을 다루지 않습니다.

## 5. 마일스톤 및 일정 (Milestones)

- ✅ **1단계(기초 구현)**: DB 풀스캔 구현 - 완료
- ✅ **2단계(Good Solution)**: DB 인덱싱 - 완료
- ✅ **3단계(Great Solution)**: In-Memory(Redis) 도입 - 완료
- ✅ **4단계(성능 검증)**: k6 부하 테스트 및 성능 최적화 - 완료

## 6. 현재 구현된 솔루션 (Current Solution)

### 🏗️ 아키텍처 개요

현재 시스템은 **Redis 캐시 + PostgreSQL 데이터베이스**로 구성된 아키텍처입니다:

- **Redis 캐시 레이어**: Cache-Aside 패턴으로 빠른 응답 제공
- **PostgreSQL 데이터베이스**: 영구 저장소 및 인덱싱
- **Spring Boot 애플리케이션**: REST API 및 비즈니스 로직 처리

### 🔧 핵심 컴포넌트

#### 1. Redis 기반 캐시 시스템 (`UrlCacheService`)

- **Cache-Aside 패턴** 구현
- **TTL 기반 자동 만료** (기본 1시간)
- **캐시 히트율 모니터링** 및 통계 수집
- **캐시 무효화** 기능

#### 2. Redis 전역 카운터 (`RedisCounterService`)

- **원자적 INCR 명령**으로 고유 ID 보장
- **분산 환경 대응** 가능
- **Base62 인코딩**으로 짧은 단축 코드 생성

#### 3. PostgreSQL 데이터베이스

- **short_code를 Primary Key**로 설정
- **B-tree 인덱싱** 자동 적용
- **URL 만료일 관리** 기능

### 📊 성능 지표

- **캐시 히트율**: 높은 캐시 효율성으로 DB 부하 최소화
- **응답 시간**: Redis 캐시 히트 시 1ms 미만
- **동시성**: Redis 원자적 연산으로 동시 요청 처리

### 🎯 실제 성능 테스트 결과

**k6 시나리오 B 테스트 결과** (1,000 VU, 5분간 부하 테스트):

| 지표             | 목표    | 실제 결과    | 상태    |
| ---------------- | ------- | ------------ | ------- |
| **에러율**       | < 1%    | 0.00%        | ✅ 달성 |
| **TPS**          | > 1,000 | 10,597 req/s | ✅ 달성 |
| **P95 응답시간** | < 100ms | 148ms        | ⚠️ 초과 |
| **총 요청**      | -       | 3,189,212개  | ✅ 안정 |

**테스트 결과 상세**: [k6 테스트 결과](./k6-tests/README.md) | [시나리오 B 가이드](./k6-tests/SCENARIO_B.md)

## 7. 기술 스택 및 환경 (Tech Stack & Environment)

### 🛠️ 개발 환경

- **언어**: Java 21
- **프레임워크**: Spring Boot 3.5.5
- **빌드 도구**: Gradle
- **데이터베이스**: PostgreSQL 13
- **캐시**: Redis 7-alpine (포트 6380)
- **컨테이너**: Docker & Docker Compose
- **성능 테스트**: k6

### 📦 주요 의존성

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    runtimeOnly 'org.postgresql:postgresql'
    compileOnly 'org.projectlombok:lombok'
}
```

### 🚀 실행 방법

1. **Docker 환경 시작**

   ```bash
   docker-compose up -d
   ```

2. **애플리케이션 실행**

   ```bash
   ./gradlew bootRun
   ```

3. **API 테스트**

   - 단축 URL 생성: `POST http://localhost:8080/urls`
   - 리다이렉션: `GET http://localhost:8080/{shortCode}`

4. **성능 테스트 실행**

   ```bash
   cd k6-tests
   k6 run --out json=results.json scenario-b-fast.js
   ```

## 8. 테스트 및 품질 보증 (Testing & Quality Assurance)

### 🧪 테스트 전략

현재 프로젝트는 **포괄적인 테스트 커버리지**를 제공합니다:

#### 단위 테스트 (Unit Tests)

- **`Base62Test`**: Base62 인코딩/디코딩 로직 검증
- **`UrlServiceTest`**: URL 단축 서비스 핵심 로직 테스트
- **`RedisCounterServiceTest`**: Redis 카운터 서비스 테스트
- **`UrlControllerTest`**: REST API 엔드포인트 테스트

#### 통합 테스트 (Integration Tests)

- **`UrlServiceIntegrationTest`**: Redis + PostgreSQL 통합 테스트
- **`UrlServiceCacheIntegrationTest`**: 캐시 시스템 통합 테스트
- **`UrlServiceAliasTest`**: 사용자 지정 alias 기능 테스트

#### 성능 테스트 (Performance Tests)

- **`UrlCacheServicePerformanceTest`**: 캐시 성능 및 부하 테스트
- **동시성 테스트**: Redis 원자적 연산 검증
- **k6 부하 테스트**: 대규모 트래픽 시나리오 검증
  - [시나리오 B 테스트 결과](./k6-tests/README.md) - 1,000 VU, 5분간 부하 테스트
  - [상세 테스트 가이드](./k6-tests/SCENARIO_B.md) - 시나리오 B 실행 및 분석

### 📊 모니터링 및 통계

- **캐시 히트율 모니터링**: Redis 기반 실시간 통계
- **응답 시간 측정**: 각 계층별 성능 지표 수집
- **에러율 추적**: 시스템 안정성 모니터링
- **Grafana 대시보드**: http://localhost:3000 (admin/admin)

### 🔧 테스트 실행 방법

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests "UrlServiceTest"

# 통합 테스트 실행 (Docker 환경 필요)
./gradlew test --tests "*IntegrationTest"

# k6 부하 테스트 실행
cd k6-tests && k6 run scenario-b-fast.js
```

## 9. API 명세 (API Specification)

### 📡 REST API 엔드포인트

#### 단축 URL 생성

- **Endpoint**: `POST /urls`
- **Content-Type**: `text/plain`
- **Request Body**: 원본 URL 문자열
- **Response**: `201 CREATED` - 생성된 단축 코드

#### 리다이렉션

- **Endpoint**: `GET /{shortCode}`
- **Response**: `302 Found` - Location 헤더에 원본 URL
- **Error**: `404 Not Found` - 유효하지 않은 단축 코드

#### 고급 기능 (현재 구현됨)

- **사용자 지정 alias**: `POST /urls` (JSON body) - ✅ 구현 완료
- **만료일 설정**: URL 만료 시간 지정 - ✅ 구현 완료

### 📋 예시 사용법

```bash
# 단축 URL 생성
curl -X POST http://localhost:8080/urls \
  -H "Content-Type: text/plain" \
  -d "https://www.example.com/very/long/url"

# 리다이렉션 테스트
curl -I http://localhost:8080/1A2B3C
```

## 10. 프로젝트 구조 (Project Structure)

```
src/
├── main/java/org/example/bitlygood/
│   ├── BitlyGoodApplication.java          # Spring Boot 메인 애플리케이션
│   ├── config/
│   │   └── RedisConfig.java              # Redis 설정 및 템플릿 구성
│   ├── controller/
│   │   ├── UrlController.java            # REST API 엔드포인트
│   │   └── MonitoringController.java     # 시스템 모니터링 API
│   ├── domain/
│   │   └── Url.java                      # URL 엔티티 (JPA)
│   ├── dto/
│   │   ├── CreateUrlRequest.java         # URL 생성 요청 DTO
│   │   └── CreateUrlResponse.java        # URL 생성 응답 DTO
│   ├── repository/
│   │   └── UrlRepository.java            # JPA Repository
│   └── service/
│       ├── Base62.java                   # Base62 인코딩/디코딩
│       ├── RedisCounterService.java      # Redis 전역 카운터
│       ├── UrlCacheService.java          # Redis 캐시 서비스
│       ├── UrlCleanupService.java        # URL 정리 서비스
│       └── UrlService.java               # 핵심 비즈니스 로직
├── test/java/org/example/bitlygood/
│   ├── controller/                       # 컨트롤러 테스트
│   └── service/                          # 서비스 테스트 (단위/통합/성능)
└── k6-tests/                            # k6 부하 테스트
    ├── README.md                        # 테스트 개요 및 결과
    ├── SCENARIO_B.md                    # 시나리오 B 상세 가이드
    ├── scenario-b-fast.js               # 빠른 테스트 스크립트
    └── scenario-b-complete.js           # 완전한 테스트 스크립트
```

## 11. 개발 로드맵 (Development Roadmap)

### ✅ 완료된 단계

- **Phase 1**: 기본 URL 단축 서비스 구현
- **Phase 2**: PostgreSQL 데이터베이스 및 인덱싱
- **Phase 3**: Redis 인메모리 캐시 도입
- **Phase 4**: 포괄적인 테스트 및 성능 최적화
- **Phase 5**: k6 부하 테스트 및 성능 검증

### 📈 성능 목표 (실제 달성 결과)

- **응답 시간**: P95 148ms (목표 100ms 미만 / 150ms 달성)
- **에러율**: 0.00% (목표 1% 미만) ✅
- **동시 처리**: 10,597 req/s (목표 1,000+ req/s) ✅
- **가용성**: 100% 안정성 (목표 99.9%) ✅
