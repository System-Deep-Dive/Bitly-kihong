/* 빠른 테스트를 위한 시나리오 B 최소 테스트 */
import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

/**
 * 시나리오 B: 최소 URL로 빠른 테스트
 * 
 * 10개 URL만 생성하여 즉시 부하 테스트를 실행합니다.
 */

// 테스트 설정
export const options = {
  scenarios: {
    // 부하 테스트
    load_test: {
      executor: 'constant-vus',
      vus: 100, // VU 수도 줄임
      duration: '1m', // 테스트 시간도 줄임
      exec: 'loadTest',
      tags: { phase: 'load_test' },
    },
  },
  
  thresholds: {
    http_req_duration: ['p(95)<100', 'p(99)<150'],
    http_req_failed: ['rate<0.01'],
    http_reqs: ['rate>100'],
  },
};

// 상수 정의
const BASE_URL = 'http://localhost:8080';

// 커스텀 메트릭 정의
const responseTimeP95 = new Trend('response_time_p95');
const responseTimeP99 = new Trend('response_time_p99');
const tpsCounter = new Counter('total_requests');
const errorRate = new Rate('error_rate');

export function setup() {
  console.log('=== 시나리오 B 최소 테스트 시작 ===');
  console.log('10개 URL만 생성하여 빠르게 테스트합니다.');
  
  const urls = { popularUrls: [], unpopularUrls: [], allUrls: [] };
  
  // 10개 URL만 생성
  for (let i = 0; i < 10; i++) {
    const originalUrl = `https://example.com/test-${i}-${Date.now()}`;
    
    const payload = JSON.stringify({
      originalUrl: originalUrl,
      alias: null,
      expiresAt: null
    });
    
    const response = http.post(`${BASE_URL}/urls`, payload, {
      headers: { 'Content-Type': 'application/json' },
      timeout: '5s'
    });
    
    if (response.status === 201) {
      try {
        const body = JSON.parse(response.body);
        const urlInfo = {
          shortCode: body.shortCode,
          originalUrl: body.originalUrl,
          popularity: Math.random() < 0.8 ? 'popular' : 'unpopular'
        };
        
        if (urlInfo.popularity === 'popular') {
          urls.popularUrls.push(urlInfo);
        } else {
          urls.unpopularUrls.push(urlInfo);
        }
        urls.allUrls.push(urlInfo);
        
        console.log(`URL ${i+1} 생성 완료: ${urlInfo.shortCode}`);
      } catch (e) {
        console.error('응답 파싱 오류:', e);
      }
    } else {
      console.error(`URL 생성 실패 (${response.status}): ${response.body}`);
    }
  }
  
  console.log(`URL 생성 완료: ${urls.allUrls.length}개`);
  console.log(`인기 URL: ${urls.popularUrls.length}개`);
  console.log(`비인기 URL: ${urls.unpopularUrls.length}개`);
  
  return {
    urlGenerationComplete: true,
    totalUrlsGenerated: urls.allUrls.length,
    startTime: Date.now(),
    urls: urls
  };
}

export function loadTest(data) {
  if (!data.urls || data.urls.allUrls.length === 0) {
    console.log('URL이 준비되지 않았습니다.');
    return;
  }
  
  // 80% 인기키 / 20% 비인기키 요청 패턴
  const isPopularRequest = Math.random() < 0.8;
  let targetUrl;
  
  if (isPopularRequest && data.urls.popularUrls.length > 0) {
    targetUrl = data.urls.popularUrls[Math.floor(Math.random() * data.urls.popularUrls.length)];
  } else if (data.urls.unpopularUrls.length > 0) {
    targetUrl = data.urls.unpopularUrls[Math.floor(Math.random() * data.urls.unpopularUrls.length)];
  } else {
    targetUrl = data.urls.allUrls[Math.floor(Math.random() * data.urls.allUrls.length)];
  }
  
  if (!targetUrl || !targetUrl.shortCode) {
    return;
  }
  
  // 리다이렉션 요청
  const response = http.get(`${BASE_URL}/${targetUrl.shortCode}`, {
    redirects: 0,
    timeout: '5s',
    tags: { 
      url_type: targetUrl.popularity,
      short_code: targetUrl.shortCode 
    },
  });
  
  // 메트릭 수집
  tpsCounter.add(1);
  
  // 응답 검증
  const isSuccess = check(response, {
    '리다이렉션 성공': (r) => r.status === 302 || r.status === 301,
    'Position 헤더 존재': (r) => r.headers['Location'] && r.headers['Location'].length > 0,
    '응답시간 < 100ms (SLO)': (r) => r.timings.duration < 100,
    '응답시간 < 50ms': (r) => r.timings.duration < 50,
  });
  
  // 메트릭 업데이트
  const redirectSuccess = response.status === 302 || response.status === 301;
  errorRate.add(!redirectSuccess);
  responseTimeP95.add(response.timings.duration);
  responseTimeP99.add(response.timings.duration);
}

export function teardown(data) {
  const endTime = Date.now();
  const totalDuration = (endTime - data.startTime) / 1000;
  
  console.log('\n=== 시나리오 B 최소 테스트 완료 ===');
  console.log(`총 테스트 시간: ${totalDuration}초`);
  console.log(`생성된 URL 수: ${data.urls.allUrls.length}개`);
  console.log(`인기 URL: ${data.urls.popularUrls.length}개`);
  console.log(`비인기 URL: ${data.urls.unpopularUrls.length}개`);
  
  console.log('\n=== 성능 분석 결과 ===');
  console.log('총 요청 수는 k6 실행 결과에서 http_reqs 메트릭으로 확인하세요.');
  console.log(`평균 TPS는 http_reqs rate 값으로 확인하세요.`);
  
  console.log('\n=== SLO 검증 결과 ===');
  console.log('P95 응답시간 SLO(100ms) 검증은 k6 리포트에서 확인하세요.');
  
  return {
    totalDuration,
    totalUrlsGenerated: data.urls.allUrls.length,
    popularUrls: data.urls.popularUrls.length,
    unpopularUrls: data.urls.unpopularUrls.length,
    testCompleted: true,
  };
}

