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
    @DisplayName("여러 URL을 순차적으로 생성할 때 고유한 카운터 값을 사용한다.")
    void createMultipleShortUrls() {
        // given
        String originalUrl1 = "https://example1.com";
        String originalUrl2 = "https://example2.com";
        String originalUrl3 = "https://example3.com";

        when(redisCounterService.getNextCounter())
                .thenReturn(1L)
                .thenReturn(2L)
                .thenReturn(3L);
        when(base62.encode(1L)).thenReturn("1");
        when(base62.encode(2L)).thenReturn("2");
        when(base62.encode(3L)).thenReturn("3");
        // save 메서드는 반환값을 테스트에서 사용하지 않으므로 when 설정 불필요

        // when
        String shortCode1 = urlService.createShortUrl(originalUrl1);
        String shortCode2 = urlService.createShortUrl(originalUrl2);
        String shortCode3 = urlService.createShortUrl(originalUrl3);

        // then
        assertEquals("1", shortCode1);
        assertEquals("2", shortCode2);
        assertEquals("3", shortCode3);

        verify(redisCounterService, times(3)).getNextCounter();
        verify(base62, times(1)).encode(1L);
        verify(base62, times(1)).encode(2L);
        verify(base62, times(1)).encode(3L);
    }
}
