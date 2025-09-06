# API 명세 및 구현 현황

## 1. API 명세

### 단축 URL 생성
- **Endpoint:** `POST /urls`
- **Description:** 원본 URL을 받아 단축 URL을 생성하고 데이터베이스에 저장합니다.
- **Request Body:**
  - `Content-Type`: `text/plain`
  - **Body:** `"https://example.com/very/long/url/to/be/shortened"`
- **Success Response:**
  - **Code:** `201 CREATED`
  - **Body:** 생성된 단축 URL (예: `"c"`)

### 원본 URL 리디렉션
- **Endpoint:** `GET /{shortUrl}`
- **Description:** 단축 URL을 받아 해당하는 원본 URL로 리디렉션합니다.
- **Path Variable:**
  - `shortUrl`: 생성된 단축 URL (예: `c`)
- **Success Response:**
  - **Code:** `302 Found`
  - **Header:** `Location: {originalUrl}`
- **Error Response:**
  - **Code:** `404 Not Found` (유효하지 않은 `shortUrl`일 경우)

---

## 2. 구현 현황

### 프로젝트 구조
- **`controller`**: `UrlController`가 API 엔드포인트를 처리합니다.
- **`service`**: `UrlService`가 핵심 비즈니스 로직을, `Base62`가 인코딩/디코딩 로직을 담당합니다.
- **`repository`**: `UrlRepository`가 데이터베이스와의 통신을 담당합니다.
- **`domain`**: `Url` 엔티티가 URL 정보를 정의합니다.

### 개발 환경
- **언어 및 프레임워크**: Java 21, Spring Boot 3.5.5
- **데이터베이스**: PostgreSQL (Docker를 통해 관리)
- **빌드 도구**: Gradle

### 설정 내용
- **`build.gradle`**: Spring Web, Spring Data JPA, Lombok, PostgreSQL 드라이버 의존성을 설정했습니다.
- **`docker-compose.yml`**: `bitly-good` 프로젝트 루트에 PostgreSQL 13 버전의 Docker 컨테이너 설정을 추가했습니다.
- **`application.properties`**: Spring 애플리케이션이 Docker 컨테이너에서 실행 중인 PostgreSQL 데이터베이스에 연결되도록 데이터 소스 정보를 설정했습니다.
