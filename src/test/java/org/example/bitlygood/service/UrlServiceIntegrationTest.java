package org.example.bitlygood.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

    @Test
    @DisplayName("10개 이상의 URL을 등록하여 단축 코드가 올바르게 생성되는지 확인")
    void createMultipleUrls_LargeScaleTest() {
        // given
        String[] originalUrls = {
                "https://www.google.com",
                "https://www.naver.com",
                "https://www.daum.net",
                "https://www.github.com",
                "https://www.stackoverflow.com",
                "https://www.youtube.com",
                "https://www.facebook.com",
                "https://www.twitter.com",
                "https://www.instagram.com",
                "https://www.linkedin.com",
                "https://www.amazon.com",
                "https://www.microsoft.com",
                "https://www.apple.com",
                "https://www.netflix.com",
                "https://www.spotify.com"
        };

        // 카운터 초기화
        redisCounterService.resetCounter(0L);

        // when
        String[] shortUrls = new String[originalUrls.length];
        for (int i = 0; i < originalUrls.length; i++) {
            shortUrls[i] = urlService.createShortUrl(originalUrls[i]);
        }

        // then
        // 1. 모든 단축 코드가 생성되었는지 확인
        assertEquals(originalUrls.length, shortUrls.length);

        // 2. 모든 단축 코드가 null이 아니고 비어있지 않은지 확인
        for (String shortCode : shortUrls) {
            assertNotNull(shortCode, "Short code should not be null");
            assertFalse(shortCode.isEmpty(), "Short code should not be empty");
            assertTrue(shortCode.length() > 0, "Short code should have at least one character");
        }

        // 3. 각 단축 코드로 원본 URL을 올바르게 조회할 수 있는지 확인
        for (int i = 0; i < shortUrls.length; i++) {
            String retrievedUrl = urlService.getOriginalUrl(shortUrls[i]);
            assertEquals(originalUrls[i], retrievedUrl,
                    "Retrieved URL should match original URL for short code: " + shortUrls[i]);
        }

        // 4. 카운터가 올바르게 증가했는지 확인
        long currentCounter = redisCounterService.getCurrentCounter();
        assertEquals(originalUrls.length, currentCounter,
                "Counter should equal the number of URLs created");
    }

    @Test
    @DisplayName("생성된 단축 코드에 중복이 없는지 검증")
    void validateShortCodeUniqueness() {
        // given
        String[] originalUrls = {
                "https://example1.com/page1",
                "https://example2.com/page2",
                "https://example3.com/page3",
                "https://example4.com/page4",
                "https://example5.com/page5",
                "https://example6.com/page6",
                "https://example7.com/page7",
                "https://example8.com/page8",
                "https://example9.com/page9",
                "https://example10.com/page10",
                "https://example11.com/page11",
                "https://example12.com/page12",
                "https://example13.com/page13",
                "https://example14.com/page14",
                "https://example15.com/page15"
        };

        // 카운터 초기화
        redisCounterService.resetCounter(0L);

        // when
        String[] shortCodes = new String[originalUrls.length];
        for (int i = 0; i < originalUrls.length; i++) {
            shortCodes[i] = urlService.createShortUrl(originalUrls[i]);
        }

        // then
        // 1. 모든 단축 코드가 고유한지 확인 (중복 검증)
        for (int i = 0; i < shortCodes.length; i++) {
            for (int j = i + 1; j < shortCodes.length; j++) {
                assertNotEquals(shortCodes[i], shortCodes[j],
                        String.format("Short codes should be unique. Found duplicate: %s at positions %d and %d",
                                shortCodes[i], i, j));
            }
        }

        // 2. 단축 코드의 길이가 적절한지 확인 (Base62 인코딩 결과 검증)
        for (String shortCode : shortCodes) {
            assertTrue(shortCode.length() >= 1 && shortCode.length() <= 10,
                    "Short code length should be reasonable: " + shortCode);
        }

        // 3. 단축 코드가 Base62 문자만 포함하는지 확인
        String base62Pattern = "^[0-9a-zA-Z]+$";
        for (String shortCode : shortCodes) {
            assertTrue(shortCode.matches(base62Pattern),
                    "Short code should only contain Base62 characters: " + shortCode);
        }

        // 4. 순차적으로 생성된 단축 코드들이 순서대로 정렬되는지 확인
        // (Redis 카운터의 순차성 보장)
        for (int i = 1; i < shortCodes.length; i++) {
            // Base62로 인코딩된 값이 순차적으로 증가하는지 확인
            Base62 base62 = new Base62();
            long currentDecoded = base62.decode(shortCodes[i]);
            long previousDecoded = base62.decode(shortCodes[i - 1]);

            assertTrue(currentDecoded > previousDecoded,
                    String.format("Sequential short codes should have increasing values. " +
                            "Previous: %s (%d), Current: %s (%d)",
                            shortCodes[i - 1], previousDecoded, shortCodes[i], currentDecoded));
        }
    }
}
