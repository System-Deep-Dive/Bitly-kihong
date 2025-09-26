package org.example.bitlygood.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bitlygood.repository.UrlRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * URL 정리 스케줄러 서비스
 * 
 * 만료된 URL을 주기적으로 정리하는 배치 작업을 수행합니다.
 * 시스템 리소스를 효율적으로 사용하고 데이터베이스 성능을 유지하기 위해 필요합니다.
 * 
 * 주요 기능:
 * - 만료된 URL 자동 삭제
 * - 캐시 무효화
 * - 정리 통계 수집
 * - 에러 처리 및 복구
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrlCleanupService {

    private final UrlRepository urlRepository;
    private final UrlCacheService urlCacheService;

    /**
     * 만료된 URL을 정리합니다.
     * 
     * 매일 새벽 2시에 실행됩니다.
     * 만료된 URL을 데이터베이스에서 삭제하고 캐시에서도 제거합니다.
     */
    @Scheduled(cron = "0 0 2 * * ?") // 매일 새벽 2시
    @Transactional
    public void cleanupExpiredUrls() {
        log.info("Starting expired URL cleanup task");

        try {
            LocalDateTime currentTime = LocalDateTime.now();

            // 만료된 URL 조회
            var expiredUrls = urlRepository.findExpiredUrls(currentTime);
            log.info("Found {} expired URLs", expiredUrls.size());

            if (!expiredUrls.isEmpty()) {
                // 캐시에서 만료된 URL 제거
                for (var url : expiredUrls) {
                    urlCacheService.evictUrl(url.getShortUrl());
                }

                // 데이터베이스에서 만료된 URL 삭제
                int deletedCount = urlRepository.deleteExpiredUrls(currentTime);
                log.info("Successfully deleted {} expired URLs", deletedCount);
            }

            log.info("Expired URL cleanup task completed successfully");

        } catch (Exception e) {
            log.error("Error during expired URL cleanup", e);
        }
    }

    /**
     * 캐시 통계를 정리합니다.
     * 
     * 매주 일요일 새벽 3시에 실행됩니다.
     * 캐시 히트율 통계를 초기화합니다.
     */
    @Scheduled(cron = "0 0 3 * * SUN") // 매주 일요일 새벽 3시
    public void resetCacheStats() {
        log.info("Resetting cache statistics");

        try {
            urlCacheService.resetCacheStats();
            log.info("Cache statistics reset completed");
        } catch (Exception e) {
            log.error("Error resetting cache statistics", e);
        }
    }

    /**
     * 시스템 상태를 확인합니다.
     * 
     * 매시간 실행됩니다.
     * 캐시 히트율과 시스템 상태를 모니터링합니다.
     */
    @Scheduled(fixedRate = 3600000) // 1시간마다
    public void monitorSystemHealth() {
        try {
            double hitRate = urlCacheService.getCacheHitRate();
            log.info("Cache hit rate: {:.2f}%", hitRate * 100);

            // 캐시 히트율이 너무 낮으면 경고
            if (hitRate < 0.5) {
                log.warn("Low cache hit rate detected: {:.2f}%", hitRate * 100);
            }

        } catch (Exception e) {
            log.error("Error monitoring system health", e);
        }
    }
}
