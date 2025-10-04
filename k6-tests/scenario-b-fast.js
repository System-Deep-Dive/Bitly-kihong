import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

/**
 * 시나리오 B: 빠른 캐시 성능 테스트 (기존 URL 재사용)
 * 
 * 이 스크립트는 기존에 생성된 URL을 재사용하여 빠르게 캐시 성능을 테스트합니다.
 * URL 생성 과정을 생략하고 즉시 부하 테스트를 시작합니다.
 */

// 테스트 설정
export const options = {
  scenarios: {
    // 부하 테스트
    load_test: {
      executor: 'constant-vus',
      vus: 1000,
      duration: '5m',
      exec: 'loadTest',
      tags: { phase: 'load_test' },
    },
  },
  
  thresholds: {
    // 현실적이면서도 도전적인 SLO 검증
    http_req_duration: ['p(95)<100', 'p(99)<150'],
    http_req_failed: ['rate<0.01'],
    // 성능 목표
    http_reqs: ['rate>1000'],
  },
};

// 상수 정의
const BASE_URL = 'http://localhost:8080';

// 커스텀 메트릭 정의
const responseTimeP95 = new Trend('response_time_p95');
const responseTimeP99 = new Trend('response_time_p99');
const tpsCounter = new Counter('total_requests');
const errorRate = new Rate('error_rate');

// 실제 존재하는 shortCode를 확인하기 위해 setup에서 동적으로 생성

export function setup() {
  console.log('=== 시나리오 B 빠른 테스트 시작 ===');
  console.log('실제 존재하는 URL을 생성하여 부하 테스트를 시작합니다.');
  
  const urls = { popularUrls: [], unpopularUrls: [], allUrls: [] };
  
  // 실제 존재하는 URL을 생성 (50개)
  for (let i = 0; i < 50; i++) {
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
  
  console.log(`테스트 URL 준비 완료: ${urls.allUrls.length}개`);
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
  // URL은 이미 준비되어 있음
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
  
  console.log('\n=== 시나리오 B 빠른 테스트 완료 ===');
  console.log(`총 테스트 시간: ${totalDuration}초`);
  console.log(`사용된 URL 수: ${data.urls.allUrls.length}개`);
  console.log(`인기 URL: ${data.urls.popularUrls.length}개`);
  console.log(`비인기 URL: ${data.urls.unpopularUrls.length}개`);
  
  // 성능 분석
  console.log('\n=== 성능 분석 결과 ===');
  console.log('총 요청 수는 k6 실행 결과에서 http_reqs 메트릭으로 확인하세요.');
  console.log(`평균 TPS는 http_reqs rate 값으로 확인하세요.`);
  
  // SLO 검증 결과
  console.log('\n=== SLO 검증 결과 ===');
  console.log('P95 응답시간 SLO(100ms) 검증은 k6 리포트에서 확인하세요.');
  console.log('Redis keyspace_hits/misses 모니터링 결과는 Redis 모니터링 로그에서 확인하세요.');
  
  return {
    totalDuration,
    totalUrlsGenerated: data.urls.allUrls.length,
    popularUrls: data.urls.popularUrls.length,
    unpopularUrls: data.urls.unpopularUrls.length,
    testCompleted: true,
  };
}

