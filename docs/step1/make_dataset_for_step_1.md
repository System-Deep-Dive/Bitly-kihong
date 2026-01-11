# 테스트 데이터셋 준비 가이드

## 개요

각 Phase에서 동일한 조건으로 테스트하기 위해 Hot/Warm/Cold 분포를 반영한 테스트 데이터셋을 미리 생성합니다.

## 데이터 분포

- **Hot set**: 상위 1% 코드가 전체 트래픽의 50%
- **Warm set**: 상위 10% 코드가 전체 트래픽의 80%
- **Cold set**: 나머지 90% 코드가 20%
- **404 케이스**: 존재하지 않는 코드 1~5%

## 데이터셋 생성

### API 응답 구조

스크립트는 `/urls` API 엔드포인트를 호출하여 단축 URL을 생성합니다. API 응답(`CreateUrlResponse`) 구조는 다음과 같습니다:

- `shortCode`: 단축 코드 (예: "1A2B3C")
- `shortUrl`: 전체 단축 URL (예: "http://localhost:8080/1A2B3C") - **API에서 제공**
- `originalUrl`: 원본 URL
- `expirationDate`: 만료일

**중요**: `shortUrl`은 API 서버에서 자동으로 생성되어 응답에 포함되므로, 스크립트에서 별도로 생성할 필요가 없습니다. `generate-dataset.py`는 API 응답의 `shortUrl`을 그대로 데이터셋에 저장합니다.

### 데이터셋 생성 실행

```bash
cd k6-tests/test-data
python generate-dataset.py --count 1000 --output step1-dataset.json
```

**옵션**:

- `--count`: 생성할 URL 개수 (기본값: 1000)
- `--output`: 출력 파일 경로 (기본값: step1-dataset.json)

**권장 설정**:

- 최소: `--count 1000` (Hot: 10개, Warm: 90개, Cold: 900개)
- 권장: `--count 10000` (Hot: 100개, Warm: 900개, Cold: 9,000개)

### 생성된 데이터셋 구조

생성된 데이터셋은 API 응답에서 받은 정보를 그대로 저장합니다:

```json
{
  "metadata": {
    "createdAt": "2025-01-XX...",
    "totalUrls": 1000,
    "distribution": {
      "hot": 10,
      "warm": 90,
      "cold": 900,
      "invalid": 20
    }
  },
  "data": {
    "hot": [
      {
        "shortCode": "abc123",
        "originalUrl": "https://example.com/test/0/1234",
        "shortUrl": "http://localhost:8080/abc123"
      },
      ...
    ],
    "warm": [...],
    "cold": [...],
    "invalid": ["INVALID000001", ...]
  }
}
```

**참고**: 각 항목의 `shortUrl`은 API 응답에서 받은 값을 그대로 사용합니다. 스크립트는 `/urls` API를 호출하고, 응답의 `shortUrl` 필드를 데이터셋에 저장합니다.

## 데이터셋 사용

### k6 시나리오에서 사용

k6 시나리오의 `setup()` 함수에서 데이터셋을 로드:

```javascript
export function setup() {
  const dataset = JSON.parse(open("./test-data/step1-dataset.json"));

  return {
    hotUrls: dataset.data.hot,
    warmUrls: dataset.data.warm,
    coldUrls: dataset.data.cold,
    invalidUrls: dataset.data.invalid
  };
}
```

### Phase별 사용 가이드

**중요**: 모든 Phase에서 **동일한 데이터셋**을 사용해야 비교가 가능합니다.

1. **Phase 1 실행 전**: 데이터셋 생성
2. **Phase 1 실행**: 생성된 데이터셋 사용
3. **Phase 2 실행**: 동일한 데이터셋 사용
4. **Phase 3 실행**: 동일한 데이터셋 사용

## 체크리스트

### 데이터셋 생성 전

- [ ] 애플리케이션이 실행 중인지 확인
- [ ] Python 설치 확인
- [ ] requests 라이브러리 설치 확인

### 데이터셋 생성

- [ ] 데이터셋 생성 스크립트 실행
- [ ] 생성된 파일 확인 (`step1-dataset.json`)
- [ ] 데이터 분포 확인 (Hot/Warm/Cold 개수)

### 각 Phase 실행 전

- [ ] 동일한 데이터셋 파일 사용 확인
- [ ] k6 시나리오에서 데이터셋 로드 확인

## 문제 해결

### API 서버 연결 실패

```
Error: Cannot connect to API server at http://localhost:8080
```

- 애플리케이션이 실행 중인지 확인
- 포트 번호 확인 (기본: 8080)

### URL 생성 실패

- API 서버 로그 확인
- 데이터베이스 연결 확인
- Redis 연결 확인 (필요한 경우)

### 데이터셋이 너무 작은 경우

- `--count` 값을 증가시켜 재생성
- 최소 1,000개 이상 권장

## 참고

- 데이터셋 파일은 Git에 커밋하지 않는 것을 권장 (용량 문제)
- 각 Phase 실행 전에 데이터셋이 존재하는지 확인
- 데이터셋을 변경하면 모든 Phase를 다시 실행해야 함
