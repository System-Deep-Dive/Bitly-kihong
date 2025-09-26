package org.example.bitlygood.service;

import org.example.bitlygood.domain.Url;
import org.example.bitlygood.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UrlCacheService 성능 테스트
 * 
 * 캐시 서비스의 성능과 동작을 검증합니다.
 * 캐시 히트/미스 시나리오와 성능 지표를 테스트합니다.
 */
@ExtendWith(MockitoExtension.class)
class UrlCacheServicePerformanceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private UrlRepository urlRepository;

    @InjectMocks
    private UrlCacheService urlCacheService;

    @BeforeEach
    void setUp() {
        // 캐시 TTL 설정 주입
        ReflectionTestUtils.setField(urlCacheService, "urlCacheTtlSeconds", 3600L);
        ReflectionTestUtils.setField(urlCacheService, "urlMetadataTtlSeconds", 86400L);
    }

    @Test
    @DisplayName("캐시 히트 시 빠른 응답 성능 테스트")
    void getOriginalUrl_CacheHit_PerformanceTest() {
        // given
        String shortCode = "test123";
        String originalUrl = "https://www.example.com";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:" + shortCode)).thenReturn(originalUrl);

        // when
        long startTime = System.nanoTime();
        Optional<String> result = urlCacheService.getOriginalUrl(shortCode);
        long endTime = System.nanoTime();

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(originalUrl);

        // 성능 검증: 캐시 히트 시 1ms 미만이어야 함
        long durationMs = (endTime - startTime) / 1_000_000;
        assertThat(durationMs).isLessThan(1);

        // 데이터베이스 호출이 없어야 함
        verify(urlRepository, never()).findByShortUrl(anyString());
    }

    @Test
    @DisplayName("캐시 미스 시 데이터베이스 조회 및 캐시 저장 테스트")
    void getOriginalUrl_CacheMiss_PerformanceTest() {
        // given
        String shortCode = "test456";
        String originalUrl = "https://www.example.com";

        Url url = new Url(originalUrl);
        url.setShortUrl(shortCode);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:" + shortCode)).thenReturn(null);
        when(urlRepository.findByShortUrl(shortCode)).thenReturn(Optional.of(url));

        // when
        Optional<String> result = urlCacheService.getOriginalUrl(shortCode);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(originalUrl);

        // 캐시에 저장되었는지 확인
        verify(valueOperations).set(eq("url:" + shortCode), eq(originalUrl), any());
        verify(valueOperations).set(eq("url_meta:" + shortCode), anyString(), any());
    }

    @Test
    @DisplayName("만료된 URL 캐시 처리 테스트")
    void getOriginalUrl_ExpiredUrl_ReturnsEmpty() {
        // given
        String shortCode = "expired123";
        String originalUrl = "https://www.example.com";

        Url expiredUrl = new Url(originalUrl, LocalDateTime.now().minusDays(1));
        expiredUrl.setShortUrl(shortCode);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:" + shortCode)).thenReturn(null);
        when(urlRepository.findByShortUrl(shortCode)).thenReturn(Optional.of(expiredUrl));

        // when
        Optional<String> result = urlCacheService.getOriginalUrl(shortCode);

        // then
        assertThat(result).isEmpty();

        // 만료된 URL은 캐시에 저장되지 않아야 함
        verify(valueOperations, never()).set(eq("url:" + shortCode), anyString(), any());
    }

    @Test
    @DisplayName("캐시 히트율 계산 테스트")
    void getCacheHitRate_CalculatesCorrectly() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("cache_stats:hits")).thenReturn("80");
        when(valueOperations.get("cache_stats:misses")).thenReturn("20");

        // when
        double hitRate = urlCacheService.getCacheHitRate();

        // then
        assertThat(hitRate).isEqualTo(0.8); // 80/(80+20) = 0.8
    }

    @Test
    @DisplayName("캐시 통계 초기화 테스트")
    void resetCacheStats_ClearsStatistics() {
        // when
        urlCacheService.resetCacheStats();

        // then
        verify(redisTemplate).delete("cache_stats:hits");
        verify(redisTemplate).delete("cache_stats:misses");
    }

    @Test
    @DisplayName("캐시에서 URL 제거 테스트")
    void evictUrl_RemovesFromCache() {
        // given
        String shortCode = "remove123";

        // when
        urlCacheService.evictUrl(shortCode);

        // then
        verify(redisTemplate).delete("url:" + shortCode);
        verify(redisTemplate).delete("url_meta:" + shortCode);
    }

    @Test
    @DisplayName("Redis 오류 시 데이터베이스 폴백 테스트")
    void getOriginalUrl_RedisError_FallbackToDatabase() {
        // given
        String shortCode = "fallback123";
        String originalUrl = "https://www.example.com";

        Url url = new Url(originalUrl);
        url.setShortUrl(shortCode);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:" + shortCode)).thenThrow(new RuntimeException("Redis error"));
        when(urlRepository.findByShortUrl(shortCode)).thenReturn(Optional.of(url));

        // when
        Optional<String> result = urlCacheService.getOriginalUrl(shortCode);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(originalUrl);

        // Redis 오류 시에도 데이터베이스에서 정상 조회되어야 함
        verify(urlRepository).findByShortUrl(shortCode);
    }
}
