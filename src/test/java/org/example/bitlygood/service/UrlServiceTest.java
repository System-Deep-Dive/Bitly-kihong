package org.example.bitlygood.service;

import org.example.bitlygood.domain.Url;
import org.example.bitlygood.repository.UrlRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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

    @InjectMocks
    private UrlService urlService;

    @Test
    @DisplayName("원본 URL을 받아 단축 URL을 생성한다.")
    void createShortUrl() {
        // given
        String originalUrl = "https://example.com";
        Url url = new Url(originalUrl);
        ReflectionTestUtils.setField(url, "id", 1L);

        when(urlRepository.save(any(Url.class))).thenReturn(url);
        when(base62.encode(1L)).thenReturn("B");

        // when
        String shortUrl = urlService.createShortUrl(originalUrl);

        // then
        assertEquals("B", shortUrl);
        verify(urlRepository, times(1)).save(any(Url.class));
        verify(base62, times(1)).encode(1L);
    }

    @Test
    @DisplayName("단축 URL로 원본 URL을 조회한다.")
    void getOriginalUrl_Success() {
        // given
        String shortUrl = "B";
        String originalUrl = "https://example.com";
        Url url = new Url(originalUrl);
        url.setShortUrl(shortUrl);

        when(urlRepository.findByShortUrl(shortUrl)).thenReturn(Optional.of(url));

        // when
        String result = urlService.getOriginalUrl(shortUrl);

        // then
        assertEquals(originalUrl, result);
    }

    @Test
    @DisplayName("존재하지 않는 단축 URL 조회 시 예외가 발생한다.")
    void getOriginalUrl_NotFound() {
        // given
        String shortUrl = "nonexistent";
        when(urlRepository.findByShortUrl(shortUrl)).thenReturn(Optional.empty());

        // when & then
        assertThrows(IllegalArgumentException.class, () -> {
            urlService.getOriginalUrl(shortUrl);
        });
    }
}
