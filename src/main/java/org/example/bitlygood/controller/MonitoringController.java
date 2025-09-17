package org.example.bitlygood.controller;

import lombok.RequiredArgsConstructor;
import org.example.bitlygood.service.UrlCacheService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 시스템 모니터링 컨트롤러
 * 
 * 시스템 성능 지표와 캐시 상태를 모니터링하기 위한 엔드포인트를 제공합니다.
 * 운영 환경에서 시스템 상태를 실시간으로 확인할 수 있습니다.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class MonitoringController {

    private final UrlCacheService urlCacheService;

    /**
     * 캐시 통계를 조회합니다.
     * 
     * @return 캐시 히트율과 관련 통계 정보
     */
    @GetMapping("/cache/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            double hitRate = urlCacheService.getCacheHitRate();
            stats.put("hitRate", hitRate);
            stats.put("hitRatePercentage", String.format("%.2f%%", hitRate * 100));
            stats.put("status", "healthy");

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            stats.put("status", "error");
            stats.put("message", e.getMessage());
            return ResponseEntity.status(500).body(stats);
        }
    }

    /**
     * 시스템 상태를 확인합니다.
     * 
     * @return 시스템 상태 정보
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> health = new HashMap<>();

        try {
            double hitRate = urlCacheService.getCacheHitRate();

            health.put("status", "UP");
            health.put("cache", Map.of(
                    "hitRate", hitRate,
                    "status", hitRate > 0.5 ? "HEALTHY" : "WARNING"));
            health.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(health);
        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            return ResponseEntity.status(503).body(health);
        }
    }
}
