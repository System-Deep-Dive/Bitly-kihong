package org.example.bitlygood.service;

import org.example.bitlygood.domain.Url;
import org.example.bitlygood.dto.CreateUrlRequest;
import org.example.bitlygood.dto.CreateUrlResponse;
import org.example.bitlygood.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * UrlService의 새로운 기능 (alias, 만료일) 테스트
 */
@ExtendWith(MockitoExtension.class)
class UrlServiceAliasTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private Base62 base62;

    @Mock
    private RedisCounterService redisCounterService;

    @Mock
    private UrlCacheService urlCacheService;

    @InjectMocks
    private UrlService urlService;

    @BeforeEach
    void setUp() {
        // 도메인 설정 주입
        ReflectionTestUtils.setField(urlService, "domain", "http://localhost:8080");
    }

    @Test
    @DisplayName("사용자 지정 alias로 URL 단축 성공")
    void createShortUrlWithAlias_Success() {
        // given
        String originalUrl = "https://www.example.com";
        String alias = "myalias";
        CreateUrlRequest request = new CreateUrlRequest(originalUrl, alias, null);

        when(urlRepository.existsByShortUrl(alias)).thenReturn(false);
        when(urlRepository.save(any(Url.class))).thenAnswer(invocation -> {
            Url url = invocation.getArgument(0);
            url.setShortUrl(alias);
            return url;
        });

        // when
        CreateUrlResponse response = urlService.createShortUrl(request);

        // then
        assertThat(response.getShortCode()).isEqualTo(alias);
        assertThat(response.getShortUrl()).isEqualTo("http://localhost:8080/" + alias);
        assertThat(response.getOriginalUrl()).isEqualTo(originalUrl);
        assertThat(response.getExpirationDate()).isNull();

        verify(urlRepository).existsByShortUrl(alias);
        verify(urlRepository).save(any(Url.class));
        verify(base62, never()).encode(anyLong());
        verify(redisCounterService, never()).getNextCounter();
    }

    @Test
    @DisplayName("사용자 지정 alias 중복 시 예외 발생")
    void createShortUrlWithAlias_DuplicateAlias_ThrowsException() {
        // given
        String originalUrl = "https://www.example.com";
        String alias = "myalias";
        CreateUrlRequest request = new CreateUrlRequest(originalUrl, alias, null);

        when(urlRepository.existsByShortUrl(alias)).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> urlService.createShortUrl(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Alias already exists: " + alias);

        verify(urlRepository).existsByShortUrl(alias);
        verify(urlRepository, never()).save(any(Url.class));
    }

    @Test
    @DisplayName("alias가 너무 짧으면 예외 발생")
    void createShortUrlWithAlias_TooShortAlias_ThrowsException() {
        // given
        String originalUrl = "https://www.example.com";
        String alias = "ab"; // 2글자 (최소 3글자)
        CreateUrlRequest request = new CreateUrlRequest(originalUrl, alias, null);

        // when & then
        assertThatThrownBy(() -> urlService.createShortUrl(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Alias must be at least 3 characters long");
    }

    @Test
    @DisplayName("alias가 너무 길면 예외 발생")
    void createShortUrlWithAlias_TooLongAlias_ThrowsException() {
        // given
        String originalUrl = "https://www.example.com";
        String alias = "a".repeat(21); // 21글자 (최대 20글자)
        CreateUrlRequest request = new CreateUrlRequest(originalUrl, alias, null);

        // when & then
        assertThatThrownBy(() -> urlService.createShortUrl(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Alias must be at most 20 characters long");
    }

    @Test
    @DisplayName("alias에 유효하지 않은 문자가 있으면 예외 발생")
    void createShortUrlWithAlias_InvalidCharacters_ThrowsException() {
        // given
        String originalUrl = "https://www.example.com";
        String alias = "my-alias"; // 하이픈 포함
        CreateUrlRequest request = new CreateUrlRequest(originalUrl, alias, null);

        // when & then
        assertThatThrownBy(() -> urlService.createShortUrl(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Alias can only contain alphanumeric characters (0-9, a-z, A-Z)");
    }

    @Test
    @DisplayName("만료일과 함께 URL 단축 성공")
    void createShortUrlWithExpirationDate_Success() {
        // given
        String originalUrl = "https://www.example.com";
        String alias = "myalias";
        String expirationDateStr = "2024-12-31T23:59:59";
        CreateUrlRequest request = new CreateUrlRequest(originalUrl, alias, expirationDateStr);

        when(urlRepository.existsByShortUrl(alias)).thenReturn(false);
        when(urlRepository.save(any(Url.class))).thenAnswer(invocation -> {
            Url url = invocation.getArgument(0);
            url.setShortUrl(alias);
            return url;
        });

        // when
        CreateUrlResponse response = urlService.createShortUrl(request);

        // then
        assertThat(response.getShortCode()).isEqualTo(alias);
        assertThat(response.getShortUrl()).isEqualTo("http://localhost:8080/" + alias);
        assertThat(response.getOriginalUrl()).isEqualTo(originalUrl);
        assertThat(response.getExpirationDate()).isEqualTo(expirationDateStr);
    }

    @Test
    @DisplayName("잘못된 만료일 형식 시 예외 발생")
    void createShortUrlWithInvalidExpirationDate_ThrowsException() {
        // given
        String originalUrl = "https://www.example.com";
        String alias = "myalias";
        String invalidExpirationDate = "2024-12-31"; // 시간 부분 누락
        CreateUrlRequest request = new CreateUrlRequest(originalUrl, alias, invalidExpirationDate);

        // when & then
        assertThatThrownBy(() -> urlService.createShortUrl(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid expiration date format");
    }

    @Test
    @DisplayName("alias 없이 자동 생성된 코드로 URL 단축 성공")
    void createShortUrlWithoutAlias_Success() {
        // given
        String originalUrl = "https://www.example.com";
        CreateUrlRequest request = new CreateUrlRequest(originalUrl, null, null);

        long counter = 12345L;
        String generatedCode = "abc123";

        when(redisCounterService.getNextCounter()).thenReturn(counter);
        when(base62.encode(counter)).thenReturn(generatedCode);
        when(urlRepository.save(any(Url.class))).thenAnswer(invocation -> {
            Url url = invocation.getArgument(0);
            url.setShortUrl(generatedCode);
            return url;
        });

        // when
        CreateUrlResponse response = urlService.createShortUrl(request);

        // then
        assertThat(response.getShortCode()).isEqualTo(generatedCode);
        assertThat(response.getShortUrl()).isEqualTo("http://localhost:8080/" + generatedCode);
        assertThat(response.getOriginalUrl()).isEqualTo(originalUrl);

        verify(redisCounterService).getNextCounter();
        verify(base62).encode(counter);
        verify(urlRepository, never()).existsByShortUrl(anyString());
    }

    @Test
    @DisplayName("만료된 URL 조회 시 예외 발생")
    void getOriginalUrl_ExpiredUrl_ThrowsException() {
        // given
        String shortCode = "myalias";
        when(urlCacheService.getOriginalUrl(shortCode)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> urlService.getOriginalUrl(shortCode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid short url");
    }

    @Test
    @DisplayName("만료일이 없는 URL 조회 성공")
    void getOriginalUrl_NoExpirationDate_Success() {
        // given
        String shortCode = "myalias";
        String originalUrl = "https://www.example.com";
        when(urlCacheService.getOriginalUrl(shortCode)).thenReturn(Optional.of(originalUrl));

        // when
        String result = urlService.getOriginalUrl(shortCode);

        // then
        assertThat(result).isEqualTo(originalUrl);
    }
}
