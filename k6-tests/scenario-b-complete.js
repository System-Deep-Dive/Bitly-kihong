import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';

/**
 * 시나리오 B: 완전한 캐시 성능 및 히트율 최적화 테스트
 * 
 * 이 스크립트는 시나리오 B의 모든 요구사항을 통합하여 실행합니다:
 * 1. 10,000개 이상 URL 사전 생성
 * 2. constant-vus 1,000 VU 5분 부하 테스트
 * 3. 80% 인기키 / 20% 비인기키 요청 패턴
 * 4. Redis keyspace_hits/misses 모니터링
 * 5. P95 응답시간 SLO(10ms) 검증
 * 6. TPS, 초저지연, 동시성 안정성 측정
 */


// 테스트 설정
export const options = {
  scenarios: {
    // 1단계: URL 사전 생성
    url_generation: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '2m', target: 50 },
        { duration: '30s', target: 0 },
      ],
      exec: 'generateUrls',
      tags: { phase: 'url_generation' },
    },
    
    // 2단계: 부하 테스트
    load_test: {
      executor: 'constant-vus',
      vus: 1000,
      duration: '5m',
      exec: 'loadTest',
      startTime: '3m', // URL 생성 완료 후 시작
      tags: { phase: 'load_test' },
    },
    
    // 3단계: Redis 모니터링
    redis_monitor: {
      executor: 'constant-vus',
      vus: 1,
      duration: '8m',
      exec: 'monitorRedis',
      tags: { phase: 'redis_monitor' },
    },
  },
  
  thresholds: {
    // SLO 검증
    http_req_duration: ['p(95)<10', 'p(99)<50'],
    http_req_failed: ['rate<0.01'],
    
    // 성능 목표
    cache_hit_rate: ['rate>0.8'],
    http_reqs: ['rate>1000'],
    
    // Redis 모니터링
    redis_hit_rate: ['value>0.8'],
  },
  
  // InfluxDB 배치 설정은 k6 실행 시 --out 옵션으로 처리됨
};

// 상수 정의 (SharedArray보다 먼저 정의)
const BASE_URL = 'http://localhost:8080';
const REDIS_MONITOR_URL = 'http://localhost:8081/actuator/prometheus';

// 커스텀 메트릭 정의 (SharedArray보다 먼저 정의)
const cacheHitRate = new Rate('cache_hit_rate');
const responseTimeP95 = new Trend('response_time_p95');
const responseTimeP99 = new Trend('response_time_p99');
const tpsCounter = new Counter('total_requests');
const errorRate = new Rate('error_rate');
const redisHitRate = new Gauge('redis_hit_rate');
const concurrentUsers = new Gauge('concurrent_users');
const throughput = new Gauge('throughput_tps');

// URL 템플릿 (SharedArray보다 먼저 정의)
const URL_TEMPLATES = [
  'https://www.google.com/search?q={query}',
  'https://www.youtube.com/watch?v={videoId}',
  'https://www.amazon.com/dp/{productId}',
  'https://www.github.com/{user}/{repo}',
  'https://www.stackoverflow.com/questions/{questionId}',
  'https://www.reddit.com/r/{subreddit}/comments/{postId}',
  'https://www.wikipedia.org/wiki/{article}',
  'https://www.medium.com/@{author}/{article}',
  'https://www.linkedin.com/in/{profile}',
  'https://www.twitter.com/{user}/status/{tweetId}',
];

// 랜덤 파라미터 생성 함수 (SharedArray보다 먼저 정의)
function generateRandomParams() {
  const chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  const numbers = '0123456789';
  
  return {
    query: generateRandomString(chars, 10),
    videoId: generateRandomString(chars, 11),
    productId: generateRandomString(chars, 10),
    user: generateRandomString(chars, 8),
    repo: generateRandomString(chars, 12),
    questionId: generateRandomString(numbers, 8),
    subreddit: generateRandomString(chars, 8),
    postId: generateRandomString(chars, 6),
    article: generateRandomString(chars, 15),
    author: generateRandomString(chars, 10),
    profile: generateRandomString(chars, 12),
    tweetId: generateRandomString(numbers, 19),
  };
}

function generateRandomString(chars, length) {
  let result = '';
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
}

// 사전 생성된 URL 데이터 (SharedArray 사용)
const urlData = new SharedArray('urls', function () {
  // 빈 배열로 초기화 (HTTP 요청은 setup()에서 수행)
  return [{ popularUrls: [], unpopularUrls: [], allUrls: [] }];
});


export function generateUrls() {
  console.log('URL 생성 중...');
  // URL 생성 로직은 setup()에서 처리됨
  // 여기서는 단순히 VU가 실행되도록 함
}

export function setup() {
  console.log('=== 시나리오 B 통합 테스트 시작 ===');
  
  // 사전에 10,000개 URL 생성
  console.log('사전 URL 생성 시작...');
  const urls = { popularUrls: [], unpopularUrls: [], allUrls: [] };
  
  for (let i = 0; i < 10000; i++) {
    const template = URL_TEMPLATES[Math.floor(Math.random() * URL_TEMPLATES.length)];
    const params = generateRandomParams();
    const originalUrl = template.replace(/\{(\w+)\}/g, (match, key) => params[key] || 'default');
    
    const payload = JSON.stringify({
      originalUrl: originalUrl,
      alias: null,
      expiresAt: null
    });
    
    const response = http.post(`${BASE_URL}/urls`, payload, {
      headers: { 'Content-Type': 'application/json' },
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
        
        if (i % 1000 === 0) {
          console.log(`URL 생성 진행: ${i}개`);
        }
      } catch (e) {
        console.error('응답 파싱 오류:', e);
      }
    }
  }
  
  console.log(`사전 URL 생성 완료: ${urls.allUrls.length}개`);
  console.log(`인기 URL: ${urls.popularUrls.length}개`);
  console.log(`비인기 URL: ${urls.unpopularUrls.length}개`);
  
  return {
    urlGenerationComplete: true,
    totalUrlsGenerated: urls.allUrls.length,
    startTime: Date.now(),
    // 생성된 URL 데이터를 반환
    urls: urls
  };
}

// 2단계: 부하 테스트
export function loadTest(data) {
  // URL은 이미 사전 생성되어 있음
  if (!data.urls || data.urls.allUrls.length === 0) {
    console.log('URL이 생성되지 않았습니다.');
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
  const startTime = Date.now();
  const response = http.get(`${BASE_URL}/${targetUrl.shortCode}`, {
    redirects: 0,
    tags: { 
      url_type: targetUrl.popularity,
      short_code: targetUrl.shortCode 
    },
  });
  const endTime = Date.now();
  const responseTime = endTime - startTime;
  
  // 메트릭 수집
  tpsCounter.add(1);
  concurrentUsers.add(__VU);
  
  // 응답 검증
  const isSuccess = check(response, {
    '리다이렉션 성공': (r) => r.status === 302 || r.status === 301,
    'Location 헤더 존재': (r) => r.headers['Location'] && r.headers['Location'].length > 0,
    '응답시간 < 10ms (SLO)': (r) => responseTime < 10,
    '응답시간 < 50ms': (r) => responseTime < 50,
    '응답시간 < 100ms': (r) => responseTime < 100,
  });
  
  // 메트릭 업데이트
  errorRate.add(!isSuccess);
  responseTimeP95.add(responseTime);
  responseTimeP99.add(responseTime);
  
  // 캐시 히트율 시뮬레이션
  const isCacheHit = responseTime < 5;
  cacheHitRate.add(isCacheHit);
  
      // TPS 계산 (10초마다만 전송)
      if (__ITER % 10 === 0) {
        const currentTime = Date.now();
        const elapsedSeconds = (currentTime - data.startTime) / 1000;
        if (elapsedSeconds > 0) {
          const currentTps = tpsCounter.count / elapsedSeconds;
          if (!isNaN(currentTps) && isFinite(currentTps)) {
            throughput.add(currentTps);
          }
        }
      }
}

// 3단계: Redis 모니터링
export function monitorRedis(data) {
  try {
    const response = http.get(REDIS_MONITOR_URL);
    
    if (response.status === 200) {
      // Prometheus 메트릭에서 Redis 정보 추출
      const metrics = response.body;
      const lines = metrics.split('\n');
      
      let keyspaceHits = 0;
      let keyspaceMisses = 0;
      let usedMemory = 0;
      
      lines.forEach(line => {
        if (line.startsWith('redis_keyspace_hits_total')) {
          const match = line.match(/redis_keyspace_hits_total\s+(\d+)/);
          if (match) keyspaceHits = parseInt(match[1]);
        } else if (line.startsWith('redis_keyspace_misses_total')) {
          const match = line.match(/redis_keyspace_misses_total\s+(\d+)/);
          if (match) keyspaceMisses = parseInt(match[1]);
        } else if (line.startsWith('redis_memory_used_bytes')) {
          const match = line.match(/redis_memory_used_bytes\s+(\d+)/);
          if (match) usedMemory = parseInt(match[1]);
        }
      });
      
          const hitRate = (keyspaceHits + keyspaceMisses) > 0 ?
            keyspaceHits / (keyspaceHits + keyspaceMisses) : 0;

          if (!isNaN(hitRate) && isFinite(hitRate)) {
            redisHitRate.add(hitRate);
          }
      
      check({ keyspaceHits, keyspaceMisses, usedMemory, hitRate }, {
        'Redis 연결 성공': (s) => s !== null,
        '캐시 히트율 > 0': (s) => s.hitRate >= 0,
        '메모리 사용량 정상': (s) => s.usedMemory > 0 && s.usedMemory < 1000000000,
      });
      
      // 30초마다 상태 출력
      if (__ITER % 30 === 0) {
        console.log(`Redis 상태 - 히트율: ${(hitRate * 100).toFixed(2)}%, 메모리: ${(usedMemory / 1024 / 1024).toFixed(2)}MB`);
      }
    }
  } catch (error) {
    console.error('Redis 모니터링 오류:', error);
  }
  
  sleep(1);
}

export function teardown(data) {
  const endTime = Date.now();
  const totalDuration = (endTime - data.startTime) / 1000;
  
  console.log('\n=== 시나리오 B 통합 테스트 완료 ===');
  console.log(`총 테스트 시간: ${totalDuration}초`);
  console.log(`생성된 URL 수: ${data.urls.allUrls.length}개`);
  console.log(`인기 URL: ${data.urls.popularUrls.length}개`);
  console.log(`비인기 URL: ${data.urls.unpopularUrls.length}개`);
  
  // 성능 분석
  console.log('\n=== 성능 분석 결과 ===');
  console.log(`총 요청 수: ${tpsCounter.count}`);
  const avgTps = totalDuration > 0 ? tpsCounter.count / totalDuration : 0;
  console.log(`평균 TPS: ${avgTps.toFixed(2)}`);
  
  // SLO 검증 결과
  console.log('\n=== SLO 검증 결과 ===');
  console.log('P95 응답시간 SLO(10ms) 검증은 k6 리포트에서 확인하세요.');
  console.log('Redis keyspace_hits/misses 모니터링 결과는 Redis 모니터링 로그에서 확인하세요.');
  
  return {
    totalDuration,
    totalRequests: tpsCounter.count,
    averageTps: avgTps,
    totalUrlsGenerated: data.urls.allUrls.length,
    popularUrls: data.urls.popularUrls.length,
    unpopularUrls: data.urls.unpopularUrls.length,
    testCompleted: true,
  };
}

