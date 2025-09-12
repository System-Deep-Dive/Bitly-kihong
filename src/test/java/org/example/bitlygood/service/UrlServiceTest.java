package org.example.bitlygood.service;

import org.example.bitlygood.domain.Url;
import org.example.bitlygood.repository.UrlRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private Base62 base62;

    @Mock
    private RedisCounterService redisCounterService;

    @InjectMocks
    private UrlService urlService;

    @Test
    @DisplayName("원본 URL을 받아 Redis 카운터를 사용하여 단축 URL을 생성한다.")
    void createShortUrl() {
        // given
        String originalUrl = "https://example.com";
        long counterValue = 1L;
        String shortCode = "1";

        when(redisCounterService.getNextCounter()).thenReturn(counterValue);
        when(base62.encode(counterValue)).thenReturn(shortCode);

        // when
        String shortUrl = urlService.createShortUrl(originalUrl);

        // then
        assertEquals(shortCode, shortUrl);
        verify(redisCounterService, times(1)).getNextCounter();
        verify(base62, times(1)).encode(counterValue);
        verify(urlRepository, times(1)).save(any(Url.class));
    }

    @Test
    @DisplayName("단축 코드로 원본 URL을 조회한다.")
    void getOriginalUrl_Success() {
        // given
        String shortCode = "1";
        String originalUrl = "https://example.com";
        Url url = new Url(originalUrl);
        url.setShortUrl(shortCode);

        when(urlRepository.findByShortUrl(shortCode)).thenReturn(Optional.of(url));

        // when
        String result = urlService.getOriginalUrl(shortCode);

        // then
        assertEquals(originalUrl, result);
        verify(urlRepository, times(1)).findByShortUrl(shortCode);
    }

    @Test
    @DisplayName("존재하지 않는 단축 코드 조회 시 예외가 발생한다.")
    void getOriginalUrl_NotFound() {
        // given
        String shortCode = "nonexistent";
        when(urlRepository.findByShortUrl(shortCode)).thenReturn(Optional.empty());

        // when & then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            urlService.getOriginalUrl(shortCode);
        });

        assertEquals("Invalid short url", exception.getMessage());
        verify(urlRepository, times(1)).findByShortUrl(shortCode);
    }

    @Test
    @DisplayName("10개 이상의 URL을 등록하여 단축 코드가 올바르게 생성되는지 확인")
    void createMultipleShortUrls_LargeScale() {
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
                "https://www.microsoft.com"
        };

        // Redis 카운터와 Base62 인코딩 모킹 설정 - 순차적으로 다른 값 반환
        when(redisCounterService.getNextCounter())
                .thenReturn(1L).thenReturn(2L).thenReturn(3L).thenReturn(4L).thenReturn(5L)
                .thenReturn(6L).thenReturn(7L).thenReturn(8L).thenReturn(9L).thenReturn(10L)
                .thenReturn(11L).thenReturn(12L);

        when(base62.encode(1L)).thenReturn("code1");
        when(base62.encode(2L)).thenReturn("code2");
        when(base62.encode(3L)).thenReturn("code3");
        when(base62.encode(4L)).thenReturn("code4");
        when(base62.encode(5L)).thenReturn("code5");
        when(base62.encode(6L)).thenReturn("code6");
        when(base62.encode(7L)).thenReturn("code7");
        when(base62.encode(8L)).thenReturn("code8");
        when(base62.encode(9L)).thenReturn("code9");
        when(base62.encode(10L)).thenReturn("code10");
        when(base62.encode(11L)).thenReturn("code11");
        when(base62.encode(12L)).thenReturn("code12");

        // when
        String[] shortCodes = new String[originalUrls.length];
        for (int i = 0; i < originalUrls.length; i++) {
            shortCodes[i] = urlService.createShortUrl(originalUrls[i]);
        }

        // then
        // 1. 모든 단축 코드가 생성되었는지 확인
        assertEquals(originalUrls.length, shortCodes.length);

        // 2. 각 단축 코드가 올바르게 생성되었는지 확인
        for (int i = 0; i < shortCodes.length; i++) {
            assertEquals("code" + (i + 1), shortCodes[i]);
        }

        // 3. 모든 서비스가 올바른 횟수로 호출되었는지 확인
        verify(redisCounterService, times(originalUrls.length)).getNextCounter();
        verify(base62, times(originalUrls.length)).encode(anyLong());
        verify(urlRepository, times(originalUrls.length)).save(any(Url.class));
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
                "https://example12.com/page12"
        };

        // Redis 카운터와 Base62 인코딩 모킹 설정 (고유한 값들)
        when(redisCounterService.getNextCounter())
                .thenReturn(1L).thenReturn(2L).thenReturn(3L).thenReturn(4L).thenReturn(5L)
                .thenReturn(6L).thenReturn(7L).thenReturn(8L).thenReturn(9L).thenReturn(10L)
                .thenReturn(11L).thenReturn(12L);

        when(base62.encode(1L)).thenReturn("code1");
        when(base62.encode(2L)).thenReturn("code2");
        when(base62.encode(3L)).thenReturn("code3");
        when(base62.encode(4L)).thenReturn("code4");
        when(base62.encode(5L)).thenReturn("code5");
        when(base62.encode(6L)).thenReturn("code6");
        when(base62.encode(7L)).thenReturn("code7");
        when(base62.encode(8L)).thenReturn("code8");
        when(base62.encode(9L)).thenReturn("code9");
        when(base62.encode(10L)).thenReturn("code10");
        when(base62.encode(11L)).thenReturn("code11");
        when(base62.encode(12L)).thenReturn("code12");

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

        // 2. 각 단축 코드가 예상된 값인지 확인
        for (int i = 0; i < shortCodes.length; i++) {
            assertEquals("code" + (i + 1), shortCodes[i]);
        }

        // 3. 모든 서비스가 올바른 횟수로 호출되었는지 확인
        verify(redisCounterService, times(originalUrls.length)).getNextCounter();
        verify(base62, times(originalUrls.length)).encode(anyLong());
        verify(urlRepository, times(originalUrls.length)).save(any(Url.class));
    }
}
