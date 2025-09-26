package org.example.bitlygood.service;

import org.example.bitlygood.dto.CreateUrlRequest;
import org.example.bitlygood.dto.CreateUrlResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * URL 서비스 통합 테스트 (캐시 포함)
 * 
 * 실제 Redis와 데이터베이스를 사용하여 전체 시스템의 동작을 검증합니다.
 * 캐시 성능과 데이터 일관성을 종합적으로 테스트합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UrlServiceCacheIntegrationTest {

    @Autowired
    private UrlService urlService;

    @Autowired
    private UrlCacheService urlCacheService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        // 테스트 전 Redis 캐시 초기화
        redisTemplate.delete(redisTemplate.keys("*"));
        urlCacheService.resetCacheStats();
    }

    @Test
    @DisplayName("URL 생성 후 캐시를 통한 조회 성능 테스트")
    void createAndRetrieveUrl_PerformanceTest() {
        // given
        String originalUrl = "https://www.example.com/performance-test";
        CreateUrlRequest request = new CreateUrlRequest(originalUrl, null, null);

        // when - URL 생성
        long createStartTime = System.nanoTime();
        CreateUrlResponse response = urlService.createShortUrl(request);
        long createEndTime = System.nanoTime();

        String shortCode = response.getShortCode();

        // 첫 번째 조회 (캐시 미스)
        long firstRetrieveStartTime = System.nanoTime();
        String firstResult = urlService.getOriginalUrl(shortCode);
        long firstRetrieveEndTime = System.nanoTime();

        // 두 번째 조회 (캐시 히트)
        long secondRetrieveStartTime = System.nanoTime();
        String secondResult = urlService.getOriginalUrl(shortCode);
        long secondRetrieveEndTime = System.nanoTime();

        // then
        assertThat(firstResult).isEqualTo(originalUrl);
        assertThat(secondResult).isEqualTo(originalUrl);

        // 성능 검증
        long createDurationMs = (createEndTime - createStartTime) / 1_000_000;
        long firstRetrieveDurationMs = (firstRetrieveEndTime - firstRetrieveStartTime) / 1_000_000;
        long secondRetrieveDurationMs = (secondRetrieveEndTime - secondRetrieveStartTime) / 1_000_000;

        System.out.printf("URL 생성 시간: %dms%n", createDurationMs);
        System.out.printf("첫 번째 조회 시간 (캐시 미스): %dms%n", firstRetrieveDurationMs);
        System.out.printf("두 번째 조회 시간 (캐시 히트): %dms%n", secondRetrieveDurationMs);

        // 캐시 히트가 캐시 미스보다 빨라야 함
        assertThat(secondRetrieveDurationMs).isLessThan(firstRetrieveDurationMs);

        // 캐시 히트율 확인
        double hitRate = urlCacheService.getCacheHitRate();
        assertThat(hitRate).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("만료일이 있는 URL 생성 및 조회 테스트")
    void createUrlWithExpiration_RetrieveBeforeAndAfterExpiration() {
        // given
        String originalUrl = "https://www.example.com/expiration-test";
        String expirationDate = LocalDateTime.now().plusMinutes(1).toString();
        CreateUrlRequest request = new CreateUrlRequest(originalUrl, "expiretest", expirationDate);

        // when
        CreateUrlResponse response = urlService.createShortUrl(request);
        String shortCode = response.getShortCode();

        // 만료 전 조회
        String resultBeforeExpiration = urlService.getOriginalUrl(shortCode);

        // then
        assertThat(resultBeforeExpiration).isEqualTo(originalUrl);

        // 만료 후 조회는 별도 테스트에서 처리 (시간이 오래 걸리므로)
        // 실제 운영에서는 스케줄러가 만료된 URL을 정리함
    }

    @Test
    @DisplayName("동시 요청 시 캐시 성능 테스트")
    void concurrentRequests_CachePerformanceTest() throws InterruptedException {
        // given
        String originalUrl = "https://www.example.com/concurrent-test";
        CreateUrlRequest request = new CreateUrlRequest(originalUrl, null, null);
        CreateUrlResponse response = urlService.createShortUrl(request);
        String shortCode = response.getShortCode();

        // 캐시 워밍업 - 첫 번째 조회로 캐시에 저장
        urlService.getOriginalUrl(shortCode);

        // when - 여러 스레드에서 동시 조회
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        String[] results = new String[threadCount];
        long[] durations = new long[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                long startTime = System.nanoTime();
                results[index] = urlService.getOriginalUrl(shortCode);
                long endTime = System.nanoTime();
                durations[index] = (endTime - startTime) / 1_000_000;
            });
        }

        // 모든 스레드 시작
        for (Thread thread : threads) {
            thread.start();
        }

        // 모든 스레드 완료 대기
        for (Thread thread : threads) {
            thread.join();
        }

        // then
        for (String result : results) {
            assertThat(result).isEqualTo(originalUrl);
        }

        // 평균 응답 시간 계산
        long totalDuration = 0;
        for (long duration : durations) {
            totalDuration += duration;
        }
        long averageDuration = totalDuration / threadCount;

        System.out.printf("동시 요청 평균 응답 시간: %dms%n", averageDuration);

        // 캐시를 사용하므로 평균 응답 시간이 빠르게 나와야 함
        assertThat(averageDuration).isLessThan(50); // 50ms 미만
    }

    @Test
    @DisplayName("캐시 무효화 테스트")
    void cacheEviction_Test() {
        // given
        String originalUrl = "https://www.example.com/eviction-test";
        CreateUrlRequest request = new CreateUrlRequest(originalUrl, "evictiontest", null);
        CreateUrlResponse response = urlService.createShortUrl(request);
        String shortCode = response.getShortCode();

        // 캐시 워밍업
        urlService.getOriginalUrl(shortCode);

        // when - 캐시에서 제거
        urlCacheService.evictUrl(shortCode);

        // then - 다시 조회 시 데이터베이스에서 가져와야 함
        String result = urlService.getOriginalUrl(shortCode);
        assertThat(result).isEqualTo(originalUrl);
    }

    @Test
    @DisplayName("시스템 상태 모니터링 테스트")
    void systemHealthMonitoring_Test() {
        // given
        String originalUrl = "https://www.example.com/health-test";
        CreateUrlRequest request = new CreateUrlRequest(originalUrl, null, null);
        CreateUrlResponse response = urlService.createShortUrl(request);
        String shortCode = response.getShortCode();

        // 캐시 워밍업 (여러 번 조회하여 히트율 증가)
        for (int i = 0; i < 5; i++) {
            urlService.getOriginalUrl(shortCode);
        }

        // when
        double hitRate = urlCacheService.getCacheHitRate();

        // then
        assertThat(hitRate).isGreaterThan(0.0);
        System.out.printf("캐시 히트율: %.2f%%%n", hitRate * 100);
    }
}
