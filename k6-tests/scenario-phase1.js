import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// 커스텀 메트릭
const errorRate = new Rate('errors');
const redirectLatency = new Trend('redirect_latency');

// 데이터셋 로드
const data = JSON.parse(open('./test-data/step1-dataset.json'));
const dataset = {
    hot: data.data.hot.map(item => item.shortCode),
    warm: data.data.warm.map(item => item.shortCode),
    cold: data.data.cold.map(item => item.shortCode),
    invalid: data.data.invalid,
};

// Base URL
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 가중치 기반 shortCode 선택
function selectShortCode() {
    const rand = Math.random();
    
    if (rand < 0.50) {
        // Hot: 50%
        const index = Math.floor(Math.random() * dataset.hot.length);
        return dataset.hot[index];
    } else if (rand < 0.80) {
        // Warm: 30%
        const index = Math.floor(Math.random() * dataset.warm.length);
        return dataset.warm[index];
    } else if (rand < 0.98) {
        // Cold: 20%
        const index = Math.floor(Math.random() * dataset.cold.length);
        return dataset.cold[index];
    } else {
        // Invalid: 2%
        const index = Math.floor(Math.random() * dataset.invalid.length);
        return dataset.invalid[index];
    }
}

// 테스트 옵션
export const options = {
    stages: [
        // 스모크 테스트: 정상 동작 확인
        { duration: '1m', target: 10 },
        // 램프업: 점진적 부하 증가
        { duration: '2m', target: 500 },
        { duration: '2m', target: 1000 },
        // 스테디 상태: 최대 부하 유지
        { duration: '3m', target: 1000 },
    ],
    thresholds: {
        // HTTP 요청 성공률
        http_req_duration: ['p(95)<500', 'p(99)<1000'],
        http_req_failed: ['rate<0.01'], // 에러율 1% 미만
        errors: ['rate<0.01'],
        // 리다이렉트 응답 시간
        redirect_latency: ['p(95)<300', 'p(99)<500'],
    },
};

// 테스트 함수
export default function () {
    const shortCode = selectShortCode();
    const url = `${BASE_URL}/${shortCode}`;
    
    const startTime = Date.now();
    const response = http.get(url, {
        redirects: 0, // 리다이렉트를 따라가지 않음
        tags: {
            category: getCategory(shortCode),
        },
    });
    
    const latency = Date.now() - startTime;
    redirectLatency.add(latency);
    
    // 응답 검증
    const isInvalid = dataset.invalid.includes(shortCode);
    const success = check(response, {
        'status is 302 or 404': (r) => {
            if (isInvalid) {
                return r.status === 404;
            }
            return r.status === 302;
        },
        'has Location header (if 302)': (r) => {
            if (r.status === 302) {
                return r.headers.Location !== undefined;
            }
            return true;
        },
    });
    
    if (!success) {
        errorRate.add(1);
    } else {
        errorRate.add(0);
    }
    
    // 요청 간 짧은 대기 시간 (현실적인 사용자 행동 시뮬레이션)
    sleep(0.1);
}

// shortCode 카테고리 판별
function getCategory(shortCode) {
    if (dataset.hot.includes(shortCode)) return 'hot';
    if (dataset.warm.includes(shortCode)) return 'warm';
    if (dataset.cold.includes(shortCode)) return 'cold';
    if (dataset.invalid.includes(shortCode)) return 'invalid';
    return 'unknown';
}

// 테스트 완료 후 리포트 생성
export function handleSummary(data) {
    // Phase는 환경 변수로 지정 가능 (기본값: phase1)
    const phase = __ENV.PHASE || 'phase1';
    return {
        [`results/results-${phase}.json`]: JSON.stringify(data, null, 2),
    };
}
