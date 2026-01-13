package org.example.bitlygood.service;

import java.time.Duration;
import java.util.Optional;

import org.example.bitlygood.domain.Url;
import org.example.bitlygood.repository.UrlRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * URL 캐시 서비스
 * 
 * Redis를 사용하여 URL 조회 성능을 최적화하는 캐시 서비스입니다.
 * Cache-Aside 패턴을 사용하여 데이터베이스 부하를 줄이고 응답 시간을 단축합니다.
 * 
 * 주요 기능:
 * - URL 조회 캐싱: 자주 조회되는 URL을 Redis에 캐시
 * - 캐시 만료 관리: TTL 기반 자동 만료
 * - 캐시 무효화: URL 변경 시 캐시 삭제
 * - 통계 수집: 캐시 히트율 및 성능 지표 수집
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrlCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final UrlRepository urlRepository;

    // 캐시 키 접두사
    private static final String URL_CACHE_PREFIX = "url:";
    private static final String URL_METADATA_PREFIX = "url_meta:";

    // 캐시 TTL 설정 (application.properties에서 주입)
    @Value("${app.cache.url.ttl:3600}") // 기본 1시간
    private long urlCacheTtlSeconds;

    @Value("${app.cache.url.metadata.ttl:86400}") // 기본 24시간
    private long urlMetadataTtlSeconds;

    /**
     * 단축코드로 원본 URL을 조회합니다. (캐시 우선)
     * 
     * Cache-Aside 패턴:
     * 1. 캐시에서 조회 시도
     * 2. 캐시 미스 시 데이터베이스에서 조회
     * 3. 데이터베이스 결과를 캐시에 저장
     * 
     * @param shortCode 조회할 단축코드
     * @return 원본 URL (Optional)
     */
    public Optional<String> getOriginalUrl(String shortCode) {
        String cacheKey = URL_CACHE_PREFIX + shortCode;

        try {
            // 1단계: 캐시에서 조회
            log.debug("Checking cache for short code: {}", shortCode);
            String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
            if (cachedUrl != null) {
                log.debug("Cache hit for short code: {}", shortCode);
                incrementCacheHitCount();
                return Optional.of(cachedUrl);
            }

            // 2단계: 캐시 미스 - 데이터베이스에서 조회
            log.debug("Cache miss for short code: {}", shortCode);
            incrementCacheMissCount();

            Optional<Url> urlOpt = urlRepository.findByShortUrl(shortCode);
            if (urlOpt.isPresent()) {
                Url url = urlOpt.get();

                // 만료 확인
                if (url.isExpired()) {
                    log.warn("URL has expired: {}", shortCode);
                    return Optional.empty();
                }

                // 3단계: 캐시에 저장
                cacheUrl(shortCode, url.getOriginalUrl());

                // 메타데이터도 캐시에 저장
                cacheUrlMetadata(shortCode, url);

                return Optional.of(url.getOriginalUrl());
            }

            return Optional.empty();

        } catch (Exception e) {
            log.error("Error retrieving URL from cache for short code: {}", shortCode, e);
            // 캐시 오류 시 데이터베이스에서 직접 조회
            return urlRepository.findByShortUrl(shortCode)
                    .filter(url -> !url.isExpired())
                    .map(Url::getOriginalUrl);
        }
    }

    /**
     * URL을 캐시에 저장합니다.
     * 
     * @param shortCode   단축코드
     * @param originalUrl 원본 URL
     */
    public void cacheUrl(String shortCode, String originalUrl) {
        log.debug("cacheUrl() called for shortCode: {}", shortCode);
        String cacheKey = URL_CACHE_PREFIX + shortCode;

        // TTL 값 검증
        long ttlSeconds = urlCacheTtlSeconds;
        if (ttlSeconds <= 0) {
            log.warn("Invalid TTL value for cache: {} seconds. Using default value: 3600 seconds", ttlSeconds);
            ttlSeconds = 3600;
        }

        try {
            redisTemplate.opsForValue().set(cacheKey, originalUrl, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.error("Error caching URL: {} (Exception type: {}, Message: {})",
                    shortCode, e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    /**
     * URL 메타데이터를 캐시에 저장합니다.
     * 
     * @param shortCode 단축코드
     * @param url       URL 엔티티
     */
    private void cacheUrlMetadata(String shortCode, Url url) {
        String metadataKey = URL_METADATA_PREFIX + shortCode;

        try {
            // 메타데이터를 JSON 형태로 저장 (간단한 문자열로 구현)
            String metadata = String.format("createdAt:%s,expirationDate:%s",
                    url.getCreatedAt(),
                    url.getExpirationDate());

            redisTemplate.opsForValue().set(metadataKey, metadata, Duration.ofSeconds(urlMetadataTtlSeconds));
            log.debug("URL metadata cached: {}", shortCode);
        } catch (Exception e) {
            log.error("Error caching URL metadata: {}", shortCode, e);
        }
    }

    /**
     * 캐시에서 URL을 제거합니다.
     * 
     * @param shortCode 제거할 단축코드
     */
    public void evictUrl(String shortCode) {
        String cacheKey = URL_CACHE_PREFIX + shortCode;
        String metadataKey = URL_METADATA_PREFIX + shortCode;

        try {
            redisTemplate.delete(cacheKey);
            redisTemplate.delete(metadataKey);
            log.debug("URL evicted from cache: {}", shortCode);
        } catch (Exception e) {
            log.error("Error evicting URL from cache: {}", shortCode, e);
        }
    }

    /**
     * 캐시 히트율을 조회합니다.
     * 
     * @return 캐시 히트율 (0.0 ~ 1.0)
     */
    public double getCacheHitRate() {
        try {
            String hitCountKey = "cache_stats:hits";
            String missCountKey = "cache_stats:misses";

            String hitsStr = redisTemplate.opsForValue().get(hitCountKey);
            String missesStr = redisTemplate.opsForValue().get(missCountKey);

            long hits = hitsStr != null ? Long.parseLong(hitsStr) : 0;
            long misses = missesStr != null ? Long.parseLong(missesStr) : 0;

            long total = hits + misses;
            return total > 0 ? (double) hits / total : 0.0;
        } catch (Exception e) {
            log.error("Error calculating cache hit rate", e);
            return 0.0;
        }
    }

    /**
     * 캐시 통계를 초기화합니다.
     */
    public void resetCacheStats() {
        try {
            redisTemplate.delete("cache_stats:hits");
            redisTemplate.delete("cache_stats:misses");
            log.info("Cache statistics reset");
        } catch (Exception e) {
            log.error("Error resetting cache statistics", e);
        }
    }

    /**
     * 캐시 히트 카운트를 증가시킵니다.
     */
    private void incrementCacheHitCount() {
        try {
            redisTemplate.opsForValue().increment("cache_stats:hits");
        } catch (Exception e) {
            log.error("Error incrementing cache hit count", e);
        }
    }

    /**
     * 캐시 미스 카운트를 증가시킵니다.
     */
    private void incrementCacheMissCount() {
        try {
            redisTemplate.opsForValue().increment("cache_stats:misses");
        } catch (Exception e) {
            log.error("Error incrementing cache miss count", e);
        }
    }
}
