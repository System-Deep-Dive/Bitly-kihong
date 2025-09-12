package org.example.bitlygood.service;

import org.example.bitlygood.domain.Url;
import org.example.bitlygood.repository.UrlRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis 카운터를 사용한 URL 단축 서비스 통합 테스트
 * 실제 Redis와 데이터베이스를 사용하여 전체 플로우를 테스트합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UrlServiceIntegrationTest {

    @Autowired
    private UrlService urlService;

    @Autowired
    private RedisCounterService redisCounterService;

    @Test
    @DisplayName("Redis 카운터를 사용한 URL 단축 전체 플로우 테스트")
    void createShortUrl_IntegrationTest() {
        // given
        String originalUrl = "https://example.com";

        // 카운터 초기화
        redisCounterService.resetCounter(0L);

        // when
        String shortCode = urlService.createShortUrl(originalUrl);

        // then
        assertNotNull(shortCode);
        assertFalse(shortCode.isEmpty());

        // 생성된 단축 코드로 원본 URL 조회 가능한지 확인
        String retrievedUrl = urlService.getOriginalUrl(shortCode);
        assertEquals(originalUrl, retrievedUrl);

        // 카운터가 증가했는지 확인
        long currentCounter = redisCounterService.getCurrentCounter();
        assertTrue(currentCounter > 0);
    }

    @Test
    @DisplayName("여러 URL을 순차적으로 생성하여 고유성과 순차성 보장 테스트")
    void createMultipleUrls_SequentialTest() {
        // given
        String[] originalUrls = {
                "https://example1.com",
                "https://example2.com",
                "https://example3.com"
        };

        // 카운터 초기화
        redisCounterService.resetCounter(0L);

        // when
        String[] shortCodes = new String[originalUrls.length];
        for (int i = 0; i < originalUrls.length; i++) {
            shortCodes[i] = urlService.createShortUrl(originalUrls[i]);
        }

        // then
        // 모든 단축 코드가 고유한지 확인
        for (int i = 0; i < shortCodes.length; i++) {
            for (int j = i + 1; j < shortCodes.length; j++) {
                assertNotEquals(shortCodes[i], shortCodes[j],
                        "Short codes should be unique");
            }
        }

        // 각 단축 코드로 원본 URL을 올바르게 조회할 수 있는지 확인
        for (int i = 0; i < shortCodes.length; i++) {
            String retrievedUrl = urlService.getOriginalUrl(shortCodes[i]);
            assertEquals(originalUrls[i], retrievedUrl);
        }

        // 카운터가 올바르게 증가했는지 확인
        long currentCounter = redisCounterService.getCurrentCounter();
        assertEquals(originalUrls.length, currentCounter);
    }

    @Test
    @DisplayName("Base62 인코딩이 올바르게 동작하는지 통합 테스트")
    void base62Encoding_IntegrationTest() {
        // given
        redisCounterService.resetCounter(0L);

        // when - 카운터 값을 직접 가져와서 Base62로 인코딩
        long counter1 = redisCounterService.getNextCounter();
        long counter2 = redisCounterService.getNextCounter();

        // then - 카운터 값이 순차적으로 증가하는지 확인
        assertEquals(1L, counter1);
        assertEquals(2L, counter2);

        // Base62 인코딩이 올바르게 동작하는지 확인
        Base62 base62 = new Base62();
        String encoded1 = base62.encode(counter1);
        String encoded2 = base62.encode(counter2);

        assertNotNull(encoded1);
        assertNotNull(encoded2);
        assertNotEquals(encoded1, encoded2);

        // 디코딩이 올바르게 동작하는지 확인
        long decoded1 = base62.decode(encoded1);
        long decoded2 = base62.decode(encoded2);

        assertEquals(counter1, decoded1);
        assertEquals(counter2, decoded2);
    }
}
